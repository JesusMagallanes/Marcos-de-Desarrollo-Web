/** Servicio `catalogo` (:8081). */
export interface Categoria {
  id: number;
  name: string;
  /** Identificador de la URL pública: /categoria/{slug}. */
  slug: string;
  description: string;
  urlImage: string | null;
}

export interface CategoriaRequest {
  name: string;
  slug: string;
  description: string;
  urlImage: string | null;
}

/** El backend valida este mismo patrón; se comparte para no duplicarlo. */
export const PATRON_SLUG = /^[a-z0-9-]+$/;

/** Genera un slug a partir del nombre. */
export function generarSlug(nombre: string): string {
  return nombre
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-|-$)/g, '');
}
