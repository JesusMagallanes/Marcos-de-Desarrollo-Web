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
}

export interface AgregarItemRequest {
  productoId: number;
  cantidad: number;
}

export interface CambiarCantidadRequest {
  cantidad: number;
}

export const CARRITO_VACIO: Carrito = { items: [], subtotal: 0 };

export function subtotalDe(item: CarritoItem): number {
  return item.precio * item.cantidad;
}

/** Impide subir la cantidad por encima de lo que hay en catálogo. */
export function puedeAumentar(item: CarritoItem): boolean {
  return item.cantidad < item.stockDisponible;
}
