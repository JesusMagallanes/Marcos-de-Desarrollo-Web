/** Servicio `compras` (:8083). */
export interface MetodoPago {
  id: number;
  name: string;
  description: string;
}

export interface MetodoPagoRequest {
  name: string;
  description: string;
}

/** El checkout con pasarela solo aplica a MercadoPago. */
export function esMercadoPago(metodo: MetodoPago): boolean {
  return metodo.name.toLowerCase().replace(/\s/g, '').includes('mercadopago');
}
