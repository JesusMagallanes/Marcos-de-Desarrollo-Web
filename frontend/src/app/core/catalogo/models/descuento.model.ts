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
