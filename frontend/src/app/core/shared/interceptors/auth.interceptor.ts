import { HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, catchError, filter, switchMap, take, throwError } from 'rxjs';
import { RUTAS_SIN_REFRESCO } from '../../usuarios/usuarios.routes';
import { ErrorApi } from '../models';
import { AuthService } from '../../usuarios/services/auth.service';

/** Adjunta el JWT y renueva la sesión de forma transparente. */
let refrescando = false;
const tokenNuevo$ = new BehaviorSubject<string | null>(null);

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const conToken = (peticion: HttpRequest<unknown>, token: string | null) =>
    token ? peticion.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : peticion;

  return next(conToken(req, auth.token)).pipe(
    catchError((error: ErrorApi) => {
      const esRutaDeAuth = RUTAS_SIN_REFRESCO.some((r) => req.url.includes(r));

      // En login o registro un 401 son credenciales malas, no sesión caducada.
      if (!error.noAutenticado || esRutaDeAuth) {
        return throwError(() => error);
      }

      if (!auth.refreshToken) {
        cerrarSesion(auth, router);
        return throwError(() => error);
      }

      return renovarYReintentar(auth, router, req, next, conToken);
    }),
  );
};

/** Si varias peticiones reciben 401 a la vez, solo una canjea el refresh token: */
function renovarYReintentar(
  auth: AuthService,
  router: Router,
  req: HttpRequest<unknown>,
  next: (r: HttpRequest<unknown>) => Observable<any>,
  conToken: (p: HttpRequest<unknown>, t: string | null) => HttpRequest<unknown>,
): Observable<any> {
  if (refrescando) {
    return tokenNuevo$.pipe(
      filter((t): t is string => t !== null),
      take(1),
      switchMap((token) => next(conToken(req, token))),
    );
  }

  refrescando = true;
  tokenNuevo$.next(null);

  return auth.refrescar().pipe(
    switchMap((token) => {
      refrescando = false;
      tokenNuevo$.next(token);
      return next(conToken(req, token));
    }),
    catchError((fallo) => {
      refrescando = false;
      cerrarSesion(auth, router);
      return throwError(() => fallo);
    }),
  );
}

function cerrarSesion(auth: AuthService, router: Router): void {
  auth.limpiarSesion();
  router.navigate(['/login'], { queryParams: { expirado: true } });
}
