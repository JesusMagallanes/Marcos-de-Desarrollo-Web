/** Paginación. */
export interface Pagina<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Página vacía, útil como valor inicial sin tener que inventar nulos. */
export function paginaVacia<T>(size = 12): Pagina<T> {
  return { content: [], number: 0, size, totalElements: 0, totalPages: 0 };
}
