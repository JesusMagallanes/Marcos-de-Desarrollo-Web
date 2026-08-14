/** Servicio `catalogo` (:8081). */
export interface Categoria {
  id: number;
  name: string;
  /** Identificador de la URL pública: /categoria/{slug}. */
  slug: string;
  description: string;
  /** Nombre de ícono de FontAwesome, sin el prefijo (p. ej. "laptop"). */
  icono: string | null;
}

export interface CategoriaRequest {
  name: string;
  slug: string;
  description: string;
  icono: string | null;
}

/** Ícono de respaldo cuando la categoría no trae uno. */
export const ICONO_CATEGORIA_POR_DEFECTO = 'tag';

export function iconoCategoria(categoria: Pick<Categoria, 'icono'>): string {
  return `fa-solid fa-${categoria.icono?.trim() || ICONO_CATEGORIA_POR_DEFECTO}`;
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
