/** Servicio `compras` (:8083). */

/**
 * Qué hace el checkout con este método.
 *
 * <p>`MERCADOPAGO` manda a la pasarela; el resto cierra el pedido en el acto
 * para cobrarlo al entregarlo.
 */
export type TipoPasarela = 'MERCADOPAGO' | 'EFECTIVO' | 'OTRO';

export interface MetodoPago {
  id: number;
  name: string;
  description: string;
  tipo: TipoPasarela;
}

export interface MetodoPagoRequest {
  name: string;
  description: string;
  tipo: TipoPasarela;
}

/**
 * El checkout con pasarela solo aplica a MercadoPago.
 *
 * <p>Se mira el TIPO y no el nombre. Con el nombre, esta función y la del
 * backend podían responder cosas distintas del mismo método —el backend dejó
 * de mirar el texto cuando la tabla ganó su columna `tipo`—, y la que decide
 * de verdad es la del backend: el botón habría prometido «Pagar con
 * MercadoPago» sobre un pedido que se iba a cerrar como contra entrega.
 */
export function esMercadoPago(metodo: MetodoPago): boolean {
  return metodo.tipo === 'MERCADOPAGO';
}
