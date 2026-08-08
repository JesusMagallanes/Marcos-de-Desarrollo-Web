/**
 * Checkout — servicio `compras` (:8083). Es la fachada de la saga de compra.
 */

/** Solo lleva el medio de pago. */
export interface PreferenciaRequest {
  metodoPagoId: number;
}

export interface PreferenciaResponse {
  id: string;
  /** URL de checkout de producción. */
  init_point: string | null;
  /** URL de checkout de pruebas. */
  sandbox_init_point: string | null;
  /** Total calculado por el backend. */
  total: number;
}

export interface ConfirmarPagoRequest {
  paymentId: string;
}

/** Estado con el que MercadoPago devuelve al comprador a /carrito. */
export type EstadoRetornoPago = 'approved' | 'failure' | 'pending';

/** Elige la URL de checkout disponible, priorizando producción. */
export function urlCheckout(pref: PreferenciaResponse): string | null {
  return pref.init_point || pref.sandbox_init_point;
}
