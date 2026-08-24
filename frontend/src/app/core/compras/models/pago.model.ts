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
/**
 * A dónde va el pedido, en partes.
 *
 * Antes era una sola línea de texto libre. Servía para imprimir una etiqueta y
 * para nada más: sin código postal no hay costo de envío, sin distrito no se
 * agrupa el reparto, y MercadoPago no acepta una cadena suelta —quiere la
 * dirección en campos separados para poder enseñarla y calcular el envío.
 *
 * La jerarquía es la peruana: departamento > provincia > distrito.
 */
export interface DireccionEntrega {
  calle: string;
  numero: string;
  /** Piso, interior, «el portón verde». Es lo que salva la entrega. */
  referencia?: string | null;
  codigoPostal: string;
  distrito: string;
  provincia: string;
  departamento: string;
  /** ISO de dos letras. Se asume PE si no viene. */
  pais?: string | null;
  /** Quien recibe, que no siempre es quien compra. */
  receptorNombre: string;
  telefonoContacto: string;
  /*
   * Opcionales: solo si el comprador acepta compartir su ubicación.
   *
   * Admiten `null` además de faltar porque así las devuelve el backend cuando
   * no hay punto guardado, y la dirección del perfil se vuelca aquí tal cual.
   */
  latitud?: number | null;
  longitud?: number | null;
}

/** Una dirección recién empezada, con lo que ya se puede dar por sabido. */
export function direccionVacia(): DireccionEntrega {
  return {
    calle: '',
    numero: '',
    referencia: '',
    codigoPostal: '',
    distrito: '',
    provincia: '',
    departamento: '',
    pais: 'PE',
    receptorNombre: '',
    telefonoContacto: '',
  };
}

/**
 * Lo que hace falta para poder entregar. Es la misma regla que aplica el
 * backend; aquí está para no mandar al comprador a MercadoPago y traerlo de
 * vuelta con un 400.
 */
export function direccionCompleta(d: DireccionEntrega): boolean {
  return (
    direccionLugarCompleto(d) &&
    d.receptorNombre.trim().length >= 3 &&
    /^[0-9]{9}$/.test(d.telefonoContacto.trim())
  );
}

/**
 * Lo que hace falta para que un SITIO esté completo, sin mirar quién recibe.
 *
 * Es lo que se guarda en el perfil: allí el nombre y el teléfono ya están en la
 * cuenta, y solo se pide el lugar. Al pagar se exige además el receptor.
 */
export function direccionLugarCompleto(d: DireccionEntrega): boolean {
  return (
    d.calle.trim().length > 0 &&
    d.numero.trim().length > 0 &&
    /^[0-9]{5}$/.test(d.codigoPostal.trim()) &&
    d.distrito.trim().length > 0 &&
    d.provincia.trim().length > 0 &&
    d.departamento.trim().length > 0
  );
}

/** La línea que se lee de un vistazo en el resumen del carrito. */
export function direccionEnUnaLinea(d: DireccionEntrega): string {
  const zona = [d.distrito, d.provincia].filter((x) => x.trim()).join(', ');
  return `${d.calle} ${d.numero}${zona ? ', ' + zona : ''}`.trim();
}

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
  entrega: DireccionEntrega;
}

export interface PreferenciaResponse {
  id: string | null;
  /** URL de checkout de producción. */
  init_point: string | null;
  /** URL de checkout de pruebas. */
  sandbox_init_point: string | null;
  /** Total calculado por el backend. */
  total: number;
  /*
   * Si hay que mandar al comprador fuera a pagar.
   *
   * El contra entrega no va a ninguna pasarela: la compra queda hecha en la
   * misma llamada. Se mira este campo y no si las URLs vienen vacías, porque
   * vacías vienen también cuando MercadoPago falla, y son lo contrario.
   */
  requierePasarela: boolean;
  /** El pedido ya creado. Solo viene cuando no hubo pasarela de por medio. */
  pedidoId: number | null;
}

export interface ConfirmarPagoRequest {
  paymentId: string;
}

/**
 * Lo que responde POST /api/pagos/verificar: la pasarela ya cobró (y con qué
 * pedido), hay un pago que aún no decide, o todavía no hay nada.
 *
 * Existe para la vuelta «a pulso» de MercadoPago, cuando no hubo back_url y por
 * tanto no llegó ningún payment_id: se pregunta en vez de adivinar.
 */
export interface VerificacionPago {
  estado: 'COMPLETADA' | 'EN_CURSO' | 'SIN_PAGO';
  /** Solo viene con COMPLETADA. */
  pedidoId: number | null;
}

/** Estado con el que MercadoPago devuelve al comprador a /carrito. */
export type EstadoRetornoPago = 'approved' | 'failure' | 'pending';

/** Elige la URL de checkout disponible, priorizando producción. */
export function urlCheckout(pref: PreferenciaResponse): string | null {
  return pref.init_point || pref.sandbox_init_point;
}
