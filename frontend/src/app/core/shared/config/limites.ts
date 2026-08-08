/** Espejo de `Limites.java` del backend. */
export const LIMITES = {
  /** Tope de `size` en endpoints paginados. */
  maxPagina: 100,
  /** Longitud máxima de un término de búsqueda. */
  maxBusqueda: 80,
  /** Máximo de identificadores en una consulta por lote. */
  maxLote: 200,
  /** Longitud máxima de un mensaje al chatbot. */
  maxMensajeChat: 500,
} as const;

/**
 * Caracteres que admite el backend en una búsqueda: letras, dígitos, espacios y unos
 * pocos signos.
 */
export const PATRON_BUSQUEDA = /^[\p{L}\p{N} .,'\-]*$/u;

/**
 * El patrón de slug NO se define aquí: ya vive en `categoria.model.ts`, que es su
 * dominio. Duplicarlo llevaría a que uno de los dos se quedara atrás.
 */

/** Deja un término de búsqueda listo para enviar. */
export function normalizarBusqueda(texto: string): string | null {
  const limpio = texto.trim().slice(0, LIMITES.maxBusqueda);
  return limpio.length > 0 ? limpio : null;
}

/** Comprueba que el término no vaya a ser rechazado por el servidor. */
export function busquedaValida(texto: string): boolean {
  const limpio = texto.trim();
  return limpio.length <= LIMITES.maxBusqueda && PATRON_BUSQUEDA.test(limpio);
}

/** Acota el tamaño de página al rango que admite el backend. */
export function acotarTamanoPagina(size: number): number {
  return Math.min(Math.max(1, Math.trunc(size)), LIMITES.maxPagina);
}
