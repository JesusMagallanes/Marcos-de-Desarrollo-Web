import { Producto } from './producto.model';

/** Servicio `catalogo` (:8081). Descuentos sobre el precio de venta. */

export type TipoDescuento = 'PORCENTAJE' | 'MONTO';

/** Aplicar un descuento a un lote de productos. */
export interface AplicarDescuentoRequest {
  productoIds: number[];
  tipo: TipoDescuento;
  /** Porcentaje (p. ej. 15) o monto en soles (p. ej. 20), según `tipo`. */
  valor: number;
  /** Vigencia como instante ISO-8601 con zona horaria (p. ej. `2026-08-11T15:00:00.000Z`). */
  inicio: string;
  fin: string;
}

/** Quitar el descuento de un lote de productos. */
export interface QuitarDescuentoRequest {
  productoIds: number[];
}

/**
 * Estado del descuento de un producto, tal como lo clasifica el servidor.
 *
 * - `programado`: hay descuento configurado pero aún no empieza.
 * - `activo`: el descuento está vigente ahora mismo.
 * - `inactivo`: sin descuento o ya vencido.
 */
export type EstadoDescuento = 'todos' | 'programado' | 'activo' | 'inactivo';

/** Cuántos productos hay en cada sección, sobre TODO el catálogo. */
export interface ConteosDescuento {
  todos: number;
  activo: number;
  programado: number;
  inactivo: number;
}

/**
 * Una página del panel de descuentos, con los conteos de las pestañas.
 *
 * Los conteos vienen con la página y no en otra llamada porque se pintan a la
 * vez: separarlos sería un segundo viaje para dibujar la misma pantalla, y un
 * momento en que las pestañas dicen un número y la lista enseña otro.
 */
export interface PaginaDescuentos {
  content: Producto[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  conteos: ConteosDescuento;
}
