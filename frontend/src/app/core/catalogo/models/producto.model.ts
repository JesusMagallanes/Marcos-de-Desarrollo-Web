/** Servicio `catalogo` (:8081). */
/** Si un producto se puede enseñar en la tienda. */
export type EstadoModeracion = 'PENDIENTE' | 'APROBADO' | 'RECHAZADO';

/** Motivo del rechazo de un producto. Se le enseña al colaborador. */
export interface RechazoProducto {
  motivo: string;
}

export interface Producto {
  id: number;
  name: string;
  description: string;
  /** Lista de especificaciones en Markdown, separada del párrafo de descripción. */
  specifications: string | null;
  /** Precio de lista (el "antes"); nunca cambia al aplicar un descuento. */
  precio: number;
  /** Precio con descuento ya calculado; null si el producto no está en oferta. */
  precioOferta: number | null;
  /** Cómo se calculó el descuento: `PORCENTAJE` o `MONTO`; null si no hay. */
  descuentoTipo: string | null;
  /** Valor del descuento: porcentaje o monto en soles; null si no hay. */
  descuentoValor: number | null;
  ofertaInicio: string | null;
  ofertaFin: string | null;
  /** Lo que paga el cliente hoy: precio de oferta vigente o precio de lista. */
  precioActual: number;
  /** true si el descuento está activo (fechas vigentes). */
  enOferta: boolean;
  /** Calificación promedio de los clientes (1-5); null si aún no hay valoraciones. */
  calificacionPromedio: number | null;
  /** Cuántas valoraciones tiene el producto. */
  cantidadValoraciones: number | null;
  /** Imagen principal (la primera de la galería), para tarjetas y carrito. */
  imageUrl: string | null;
  /** Galería completa de imágenes, ordenada. */
  imagenes: string[];
  stock: number;
  categoriaId: number;
  categoriaName: string;
  marcaId: number | null;
  marcaName: string | null;
  /* ── Dueño y moderación (SZ-B08) ── */
  /** `null` = producto de la tienda. */
  propietarioId: number | null;
  estadoModeracion: EstadoModeracion;
  /** Sólo con texto si está RECHAZADO. */
  motivoRechazo: string | null;
}

export interface ProductoRequest {
  name: string;
  description: string;
  specifications: string | null;
  precio: number;
  imagenes: string[];
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

/** Etiqueta del descuento para la tienda: `-15%` o `-S/ 20`. */
export function etiquetaDescuento(p: Producto): string {
  if (!p.enOferta || p.descuentoValor == null) return '';
  if (p.descuentoTipo === 'MONTO') return `-S/ ${p.descuentoValor}`;
  return `-${p.descuentoValor}%`;
}

/** Porcentaje de descuento redondeado (solo si el producto está en oferta). */
export function porcentajeDescuento(
  p: Pick<Producto, 'precio' | 'precioActual' | 'enOferta'>,
): number {
  if (!p.enOferta || !p.precio) return 0;
  return Math.max(0, Math.round(((p.precio - p.precioActual) / p.precio) * 100));
}
