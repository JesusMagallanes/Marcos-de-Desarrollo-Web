/** Servicio `catalogo` (:8081). */
export interface Producto {
  id: number;
  name: string;
  description: string;
  precio: number;
  imageUrl: string | null;
  stock: number;
  categoriaId: number;
  categoriaName: string;
  marcaId: number | null;
  marcaName: string | null;
}

export interface ProductoRequest {
  name: string;
  description: string;
  precio: number;
  imageUrl: string | null;
  stock: number;
  categoriaId: number;
  marcaId: number | null;
}

/** Imagen de respaldo cuando el producto no trae una. */
export const IMAGEN_POR_DEFECTO = '/Img/img.png';

export function imagenDe(producto: Pick<Producto, 'imageUrl'>): string {
  return producto.imageUrl?.trim() || IMAGEN_POR_DEFECTO;
}

export function sinStock(producto: Pick<Producto, 'stock'>): boolean {
  return producto.stock <= 0;
}
