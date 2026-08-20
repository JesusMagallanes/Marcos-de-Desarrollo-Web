/** Servicio `compras` (:8083). El carrito siempre es el del usuario del token. */
export interface CarritoItem {
  itemId: number;
  productId: number;
  /** Copia traída de catálogo al construir la respuesta. */
  nombre: string;
  precio: number;
  cantidad: number;
  image: string | null;
  /** Stock vigente en catálogo, para no dejar pedir de más. */
  stockDisponible: number;
}

export interface Carrito {
  items: CarritoItem[];
  /** Calculado en el servidor; el cliente nunca envía importes. */
  subtotal: number;
  /*
   * El envío y el total también vienen del servidor, y no se recomponen aquí.
   *
   * Antes el frontend traía solo el subtotal y sumaba su propia copia del
   * umbral y del costo. Eran dos escrituras de la misma regla de negocio, y la
   * que se cobraba de verdad era la del backend — que ni siquiera sumaba el
   * envío: el carrito enseñaba 215 y en la pasarela se cobraban 200.
   */
  costoEnvio: number;
  /** Lo que se va a cobrar: subtotal + envío. */
  total: number;
}

export interface AgregarItemRequest {
  productoId: number;
  cantidad: number;
}

export interface CambiarCantidadRequest {
  cantidad: number;
}

export const CARRITO_VACIO: Carrito = { items: [], subtotal: 0, costoEnvio: 0, total: 0 };

export function subtotalDe(item: CarritoItem): number {
  return item.precio * item.cantidad;
}

/** Impide subir la cantidad por encima de lo que hay en catálogo. */
export function puedeAumentar(item: CarritoItem): boolean {
  return item.cantidad < item.stockDisponible;
}
