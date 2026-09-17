import { Producto } from './producto.model';

/** Servicio `catalogo` (:8081). Endpoint público. */
export interface TurnoChat {
  autor: 'usuario' | 'asistente';
  /** Texto plano: el HTML de las respuestas se quita antes de mandarlo. */
  texto: string;
}

export interface MensajeChat {
  mensaje: string;
  /** Últimos turnos, para que el asistente entienda «¿y en 16 GB?». Máximo 8. */
  historial?: TurnoChat[];
}

export type TipoRespuestaChat =
  | 'ofertas'
  | 'envio'
  | 'pago'
  | 'contacto'
  | 'categoria'
  | 'busqueda'
  | 'saludo'
  | 'gracias'
  | 'ayuda'
  | 'ia'
  | 'error';

export interface RespuestaChat {
  /** HTML montado por el backend, que ya escapa los nombres de producto. */
  respuesta: string;
  tipo: TipoRespuestaChat;
  productos: Producto[];
  categoria: string | null;
}

/** Límite que valida el backend; el formulario lo replica. */
export const MAX_LONGITUD_MENSAJE = 500;
