import { HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { Observable, retry, timer, throwError } from 'rxjs';
import { ErrorApi } from '../models';

/** Reintenta los fallos que suelen resolverse solos. */
const MAX_INTENTOS = 2;
const ESPERA_BASE_MS = 400;

/** Cupo excedido: respetar el Retry-After sería esperar minutos. Mejor avisar. */
const MAX_ESPERA_MS = 3_000;

export const reintentoInterceptor: HttpInterceptorFn = (req, next) => {
  if (!esIdempotente(req)) {
    return next(req);
  }

  return next(req).pipe(
    retry({
      count: MAX_INTENTOS,
      delay: (error: ErrorApi, intento: number) => esperar(error, intento),
    }),
  );
};

function esIdempotente(req: HttpRequest<unknown>): boolean {
  return req.method === 'GET' || req.method === 'HEAD';
}

function esperar(error: ErrorApi, intento: number): Observable<number> {
  // `transitorio` lo pone errorInterceptor: red caída, 503 o 429.
  if (!error?.transitorio) {
    return throwError(() => error);
  }

  // Con un 429 el servidor dice cuánto esperar. Si pide más de lo razonable
  // para una interacción, se rinde y deja que la interfaz lo explique.
  if (error.limitado) {
    const pedido = (error.reintentarEn ?? 0) * 1000;
    if (pedido > MAX_ESPERA_MS) {
      return throwError(() => error);
    }
    return timer(Math.max(pedido, ESPERA_BASE_MS));
  }

  // Espera exponencial: 400 ms, 800 ms.
  return timer(ESPERA_BASE_MS * 2 ** (intento - 1));
}
