/** Errores de la API, normalizados. */

/** Tal como lo emiten los servicios Spring. */
export interface ProblemDetail {
  type?: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  /** Presente en los 400 de validación: campo → mensaje. */
  errores?: Record<string, string>;
}

/** Error ya normalizado por el interceptor. */
export interface ErrorApi {
  /** Código HTTP; 0 si la petición no llegó a salir (red caída). */
  estado: number;
  /** Mensaje listo para mostrar al usuario. */
  mensaje: string;
  /** Errores por campo, si el backend los envió. */
  camposInvalidos: Record<string, string>;
  /** Identificador de correlación de la petición. */
  correlacionId: string | null;
  /** Segundos que pide esperar el servidor antes de reintentar. */
  reintentarEn: number | null;

  readonly noAutenticado: boolean;
  readonly sinPermiso: boolean;
  readonly noEncontrado: boolean;
  readonly conflicto: boolean;
  readonly servicioCaido: boolean;
  /** 429: se excedió el cupo de peticiones del servidor. */
  readonly limitado: boolean;
  /** 400: datos o parámetros rechazados por validación. */
  readonly entradaInvalida: boolean;
  /**
   * Fallos que suelen resolverse solos: red caída, servicio no disponible o cupo
   * excedido. Es lo que decide si merece la pena reintentar.
   */
  readonly transitorio: boolean;
}

/** Mensaje del primer campo inválido, para formularios de un solo error. */
export function primerCampoInvalido(error: ErrorApi): string | null {
  const campos = Object.values(error.camposInvalidos);
  return campos.length > 0 ? campos[0] : null;
}

/** Texto para reportar una incidencia a soporte. */
export function referenciaDeSoporte(error: ErrorApi): string {
  return error.correlacionId
    ? `${error.mensaje} (referencia: ${error.correlacionId})`
    : error.mensaje;
}
