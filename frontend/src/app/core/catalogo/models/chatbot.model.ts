import { Producto } from './producto.model';

/** Servicio `catalogo` (:8081). Endpoint público. */
export interface MensajeChat {
  mensaje: string;
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
