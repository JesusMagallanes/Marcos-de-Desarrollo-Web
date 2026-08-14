/** Fase de moderación de una valoración. */
export type EstadoValoracion = 'PENDIENTE' | 'APROBADA' | 'RECHAZADA';

export const ESTADOS_VALORACION: readonly EstadoValoracion[] = [
  'PENDIENTE',
  'APROBADA',
  'RECHAZADA',
] as const;

export const ETIQUETAS_VALORACION: Record<EstadoValoracion, string> = {
  PENDIENTE: 'Pendiente',
  APROBADA: 'Aprobada',
  RECHAZADA: 'Rechazada',
};

/** Valoración de un cliente sobre un producto (servicio `catalogo`). */
export interface Valoracion {
  id: number;
  /** Nombre mostrado del cliente que valoró. */
  nombre: string;
  /** De 1 a 5 estrellas. */
  calificacion: number;
  comentario: string;
  /** Estado de moderación; solo lo ve la tienda si es APROBADA. */
  estado: EstadoValoracion;
  creadoEn: string;
}

/** Vista del panel de moderación: incluye el producto reseñado. */
export interface ValoracionAdmin extends Valoracion {
  productoId: number;
  productoNombre: string;
  actualizadoEn: string;
}

/** Cuerpo para crear o actualizar la valoración del usuario en curso. */
export interface ValoracionRequest {
  calificacion: number;
  comentario: string;
  nombre: string;
}
