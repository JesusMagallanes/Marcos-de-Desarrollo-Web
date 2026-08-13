/** Guías de ayuda ("Aprende con nosotros") — servicio `catalogo`. */

/** Lo que necesita la tarjeta del listado. */
export interface GuiaResumen {
  id: number;
  slug: string;
  titulo: string;
  resumen: string;
  icono: string | null;
  posicion: number;
  publicada: boolean;
  totalPasos: number;
}

export interface Paso {
  id: number;
  posicion: number;
  titulo: string;
  descripcion: string;
}

export interface Guia extends Omit<GuiaResumen, 'totalPasos'> {
  pasos: Paso[];
}

export interface PasoRequest {
  titulo: string;
  descripcion: string;
}

export interface GuiaRequest {
  slug: string;
  titulo: string;
  resumen: string;
  icono: string | null;
  posicion: number;
  publicada: boolean;
  pasos: PasoRequest[];
}

/** Icono de respaldo cuando la guía no trae uno. */
export const ICONO_GUIA_POR_DEFECTO = 'circle-question';

export function iconoDe(guia: Pick<GuiaResumen, 'icono'>): string {
  return `fa-solid fa-${guia.icono?.trim() || ICONO_GUIA_POR_DEFECTO}`;
}

/**
 * Genera el slug a partir del título, para no obligar al administrador a
 * escribirlo a mano. Quita tildes, pasa a minúsculas y deja solo letras,
 * números y guiones, que es lo que admite el backend.
 */
export function slugDesdeTitulo(titulo: string): string {
  return titulo
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 140);
}
