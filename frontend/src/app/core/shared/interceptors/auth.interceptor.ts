import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, EMPTY, Observable, catchError, filter, switchMap, take, throwError } from 'rxjs';
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
    catchError((error: ErrorApi | HttpErrorResponse) => {
      const esRutaDeAuth = RUTAS_SIN_REFRESCO.some((r) => req.url.includes(r));

      // En login o registro un 401 son credenciales malas, no sesión caducada.
      if (!esNoAutenticado(error) || esRutaDeAuth) {
        return throwError(() => error);
      }

      if (!auth.refreshToken) {
        return cerrarSesion(auth, router);
      }

      return renovarYReintentar(auth, router, req, next, conToken);
    }),
  );
};

/**
 * ¿Es un 401?
 *
 * <p>Hay que mirar las DOS formas del error, y esto no es defensivo: es que
 * llegan las dos. Los interceptores se declaran en el orden
 * `[correlacion, error, reintento, auth]`, y en la respuesta se recorren al
 * revés, así que ESTE interceptor es el primero en ver el fallo — todavía como
 * `HttpErrorResponse` crudo. La conversión a `ErrorApi`, con su `noAutenticado`,
 * ocurre después, en el interceptor de errores.
 *
 * <p>Mirar solo `noAutenticado` era el fallo: para una respuesta real venía
 * `undefined`, se daba por "no es un 401" y **el refresco no se intentaba
 * nunca**. Cada token caducado acababa en la pantalla como "Necesitas iniciar
 * sesión para esta operación" aunque el usuario tuviera su sesión abierta y un
 * refresh token perfectamente válido.
 */
function esNoAutenticado(error: ErrorApi | HttpErrorResponse): boolean {
  return 'noAutenticado' in error ? error.noAutenticado : error.status === 401;
}

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
    catchError(() => {
      refrescando = false;
      return cerrarSesion(auth, router);
    }),
  );
}

/**
 * La sesión se perdió: se lleva al usuario al login y **la petición deja de
 * emitir**.
 *
 * <p>Devolver `EMPTY` en vez de propagar el error es lo que arregla el sintoma
 * que se veia en pantalla: una linea suelta que decia "Necesitas iniciar sesion
 * para esta operacion". Ese texto viene del backend y es correcto como
 * respuesta HTTP, pero no es algo que deba pintarse en la pagina: al usuario no
 * le sirve de nada leerlo, porque ya se le esta llevando al login.
 *
 * <p>Al no emitir, los componentes se quedan en su estado de carga --con la
 * animacion del logo-- hasta que el enrutador cambia de pagina. Es lo que se ve
 * al arrancar la app, y por tanto lo que el usuario ya reconoce.
 *
 * <p>Esto vale para TODAS las peticiones: la que provoco el 401, las que
 * estuvieran esperando el refresco y las que vengan despues.
 */
function cerrarSesion(auth: AuthService, router: Router): Observable<never> {
  auth.limpiarSesion();
  router.navigate(['/login'], { queryParams: { expirado: true } });
  return EMPTY;
}
