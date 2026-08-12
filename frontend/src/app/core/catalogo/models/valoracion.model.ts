/** Valoración de un cliente sobre un producto (servicio `catalogo`). */
export interface Valoracion {
  id: number;
  /** Nombre mostrado del cliente que valoró. */
  nombre: string;
  /** De 1 a 5 estrellas. */
  calificacion: number;
  comentario: string;
  creadoEn: string;
}

/** Cuerpo para crear o actualizar la valoración del usuario en curso. */
export interface ValoracionRequest {
  calificacion: number;
  comentario: string;
  nombre: string;
}
