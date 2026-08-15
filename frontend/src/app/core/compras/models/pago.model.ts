/**
 * Checkout — servicio `compras` (:8083). Es la fachada de la saga de compra.
 */

/**
 * Medio de pago y destino. El importe NO viaja: lo recalcula el backend desde
 * el carrito.
 *
 * La dirección se manda al iniciar el checkout y no al confirmarlo, porque
 * después el comprador se va a MercadoPago y puede no volver: si no se pidiera
 * aquí, el pedido quedaría pagado y sin destino.
 */
export interface PreferenciaRequest {
  metodoPagoId: number;
  direccionEnvio: string;
  referenciaEnvio?: string;
  telefonoContacto: string;
  /** Opcionales: solo si el comprador acepta compartir su ubicación. */
  latitud?: number;
  longitud?: number;
}

/** Lo que el usuario rellena en el bloque de entrega del carrito. */
export interface DatosEntrega {
  direccionEnvio: string;
  referenciaEnvio: string;
  telefonoContacto: string;
  latitud?: number;
  longitud?: number;
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
