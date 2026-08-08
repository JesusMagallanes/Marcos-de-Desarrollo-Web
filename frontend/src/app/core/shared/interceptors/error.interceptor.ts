import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { CABECERA_CORRELACION } from '../config/constantes';
import { ErrorApi, ProblemDetail } from '../models';

/** Convierte cualquier fallo HTTP en un {@link ErrorApi} uniforme. */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req).pipe(
    catchError((fallo: HttpErrorResponse) => throwError(() => normalizar(fallo))),
  );
};

function normalizar(fallo: HttpErrorResponse): ErrorApi {
  const estado = fallo.status ?? 0;
  const cuerpo = fallo.error as Partial<ProblemDetail> | string | null;
  const problema = typeof cuerpo === 'object' && cuerpo !== null ? cuerpo : null;

  const limitado = estado === 429;
  const servicioCaido = estado === 503 || estado === 0;

  return {
    estado,
    mensaje: mensajeDe(estado, problema),
    camposInvalidos: problema?.errores ?? {},
    correlacionId: correlacionDe(fallo),
    reintentarEn: limitado ? segundosDeEspera(fallo) : null,

    noAutenticado: estado === 401,
    sinPermiso: estado === 403,
    noEncontrado: estado === 404,
    conflicto: estado === 409,
    entradaInvalida: estado === 400 || estado === 422,
    limitado,
    servicioCaido,
    transitorio: servicioCaido || limitado,
  };
}

/** Extrae el identificador de correlación. */
function correlacionDe(fallo: HttpErrorResponse): string | null {
  const valor = fallo.headers?.get(CABECERA_CORRELACION);
  if (!valor) {
    return null;
  }
  return valor.split(',')[0].trim() || null;
}

/** Lee `Retry-After` de la respuesta 429. */
function segundosDeEspera(fallo: HttpErrorResponse): number | null {
  const cabecera = fallo.headers?.get('Retry-After');
  if (!cabecera) {
    return null;
  }

  const segundos = Number(cabecera);
  if (Number.isFinite(segundos)) {
    return Math.max(1, Math.round(segundos));
  }

  const fecha = Date.parse(cabecera);
  if (!Number.isNaN(fecha)) {
    return Math.max(1, Math.round((fecha - Date.now()) / 1000));
  }
  return null;
}

/**
 * Prioriza el `detail` que envía el backend, que ya está redactado para el usuario
 * ("Solo quedan 2 unidades de X"). Solo si no viene se recurre a un texto genérico por
 * código.
 */
function mensajeDe(estado: number, problema: Partial<ProblemDetail> | null): string {
  if (problema?.detail) {
    return problema.detail;
  }

  switch (estado) {
    case 0:
      return 'No hay conexión con el servidor.';
    case 400:
      return 'Los datos enviados no son válidos.';
    case 401:
      return 'Necesitas iniciar sesión.';
    case 403:
      return 'No tienes permiso para esta operación.';
    case 404:
      return 'No encontramos lo que buscabas.';
    case 405:
      return 'Esa operación no está permitida aquí.';
    case 409:
      return 'La operación entra en conflicto con el estado actual.';
    case 422:
      return 'Los datos enviados no se pudieron procesar.';
    case 429:
      return 'Demasiadas peticiones. Espera un momento.';
    case 503:
      return 'El servicio no está disponible. Intenta en unos minutos.';
    default:
      return estado >= 500
        ? 'Ocurrió un error en el servidor.'
        : 'Ocurrió un error inesperado.';
  }
}
