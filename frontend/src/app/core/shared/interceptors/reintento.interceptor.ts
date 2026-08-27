import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { Observable, retry, timer, throwError } from 'rxjs';

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
      delay: (error: unknown, intento: number) => esperar(error, intento),
    }),
  );
};

function esIdempotente(req: HttpRequest<unknown>): boolean {
  return req.method === 'GET' || req.method === 'HEAD';
}

/**
 * En la cadena de interceptores, `reintento` recibe el error ANTES que
 * `errorInterceptor` lo normalice: aquí el error sigue siendo un
 * `HttpErrorResponse` crudo, no un `ErrorApi`. Se comprueba el status
 * directamente en vez de depender de `transitorio`.
 */
function esperar(error: unknown, intento: number): Observable<number> {
  /*
   * Lo que no es una respuesta HTTP no se reintenta.
   *
   * Aquí también acaba cualquier excepción lanzada más adentro de la cadena
   * —un TypeError al mapear la respuesta, por ejemplo—, y esos no se arreglan
   * repitiendo: solo se ejecutarían tres veces y el fallo real llegaría tarde
   * y por triplicado. El status 0 es de verdad transitorio SOLO cuando viene
   * en un HttpErrorResponse, que es como Angular dice «no hubo respuesta».
   */
  if (!(error instanceof HttpErrorResponse)) {
    return throwError(() => error);
  }

  const estado = error.status;

  // Solo reintentar fallos transitorios: red caída (0), 503, 429.
  const transitorio = estado === 0 || estado === 503 || estado === 429;
  if (!transitorio) {
    return throwError(() => error);
  }

  // Con un 429 el servidor dice cuánto esperar. Si pide más de lo razonable
  // para una interacción, se rinde y deja que la interfaz lo explique.
  if (estado === 429) {
    const cabecera = error.headers?.get('Retry-After');
    const segundos = cabecera ? Number(cabecera) : null;
    const pedido = segundos && Number.isFinite(segundos) ? segundos * 1000 : 0;
    if (pedido > MAX_ESPERA_MS) {
      return throwError(() => error);
    }
    return timer(Math.max(pedido, ESPERA_BASE_MS));
  }

  // Espera exponencial: 400 ms, 800 ms.
  return timer(ESPERA_BASE_MS * 2 ** (intento - 1));
}
