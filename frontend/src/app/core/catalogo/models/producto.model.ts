/** Una característica del producto, con su unidad separada del valor. */
export interface AtributoValor {
  /** Clave natural estable: `pulgadas`, `ram_gb`. Es lo que hace comparables dos productos. */
  codigo: string;
  nombre: string;
  valor: string;
  unidad: string | null;
  /** Copia numérica cuando el atributo lo es; permite ordenar y filtrar por rango. */
  valorNumero: number | null;
}

import { Categoria } from './categoria.model';

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
  /**
   * Las características, ya estructuradas (V19 del backend).
   *
   * <p>`specifications` sigue llegando con el mismo texto de siempre, pero es
   * un DERIVADO de esto. Lo nuevo debería leer de aquí: es lo único que permite
   * filtrar y comparar sin parsear Markdown.
   */
  atributos?: AtributoValor[];
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

/** Lo que devuelve subir una foto: la URL relativa lista para el formulario. */
export interface ImagenSubida {
  url: string;
  tipoMime: string;
  tamanoBytes: number;
}

/** Tope de una foto de producto; el backend aplica el mismo (`AlmacenImagenes`). */
export const IMAGEN_TAMANO_MAXIMO = 5 * 1024 * 1024;

/** Lo que el almacén acepta, decidido allí por los bytes; aquí solo se filtra el selector. */
export const IMAGEN_TIPOS_ACEPTADOS = ['image/jpeg', 'image/png', 'image/webp'] as const;

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

/**
 * Lo que la portada necesita, tal y como lo devuelve el servidor.
 *
 * Antes la portada se descargaba el catálogo COMPLETO y componía estas tres
 * listas en el navegador: los diez primeros, los que tuvieran descuento vigente
 * y doce por categoría. Ninguna necesitaba el catálogo entero, y el catálogo
 * entero es lo que se traía en cada visita.
 */
export interface Portada {
  destacados: Producto[];
  ofertas: Producto[];
  porCategoria: BloqueCategoria[];
}

export interface BloqueCategoria {
  categoria: Categoria;
  productos: Producto[];
}

/* ══════════════ Búsqueda y filtrado con facetas ══════════════ */

/**
 * Orden del listado. Los valores son los que viajan en la URL; el servicio los
 * traduce al enumerado del backend (`precio-asc` → `PRECIO_ASC`).
 */
export type OrdenCatalogo =
  | 'relevancia'
  | 'precio-asc'
  | 'precio-desc'
  | 'nombre-asc'
  | 'nombre-desc'
  | 'novedad';

/**
 * El estado de un filtro de catálogo, el mismo para búsqueda y para categoría.
 *
 * <p>`atributos` son cadenas `codigo:valor`, la forma que ya usaba la selección
 * de características. Varios códigos distintos se exigen todos (AND); dos valores
 * del mismo código son alternativa (OR). Esa semántica la resuelve el servidor;
 * aquí solo se transporta la lista.
 */
export interface FiltroCatalogo {
  precioMin: number | null;
  precioMax: number | null;
  marcaIds: number[];
  atributos: string[];
  soloDisponibles: boolean;
  orden: OrdenCatalogo;
}

/** Un filtro sin nada seleccionado. */
export function filtroVacio(): FiltroCatalogo {
  return {
    precioMin: null,
    precioMax: null,
    marcaIds: [],
    atributos: [],
    soloDisponibles: false,
    orden: 'relevancia',
  };
}

/**
 * Si el filtro estrecha de verdad el conjunto.
 *
 * <p>El orden no cuenta como «activo» para decidir si hay filtro, pero sí para
 * saltarse la caché: una página cacheada viene en el orden por defecto.
 */
export function filtroActivo(f: FiltroCatalogo | null | undefined): boolean {
  if (!f) return false;
  return (
    f.precioMin != null ||
    f.precioMax != null ||
    f.marcaIds.length > 0 ||
    f.atributos.length > 0 ||
    f.soloDisponibles
  );
}

/** El token del backend para un orden. `precio-asc` → `PRECIO_ASC`. */
export function ordenBackend(orden: OrdenCatalogo): string {
  return orden.replace(/-/g, '_').toUpperCase();
}

/** Las facetas de un conjunto de resultados, tal como las da el servidor. */
export interface FacetasCatalogo {
  total: number;
  disponibles: number;
  marcas: FacetaMarca[];
  atributos: FacetaAtributo[];
}

export interface FacetaMarca {
  id: number;
  nombre: string;
  conteo: number;
}

export interface FacetaAtributo {
  codigo: string;
  nombre: string;
  valores: FacetaValor[];
}

export interface FacetaValor {
  valor: string;
  conteo: number;
}

/**
 * Combina el UNIVERSO de opciones con los CONTEOS del conjunto ya filtrado.
 *
 * <p>El universo se lee una vez (sin filtros, solo el texto o la categoría) y no
 * cambia: es lo que mantiene utilizable el multi-select. Si al elegir «Lenovo»
 * las facetas vivas solo devolvieran «Lenovo», ya no se podría añadir otra marca
 * ni otro valor del mismo atributo (que es un OR). Así que las opciones salen
 * del universo y solo el número «(7)» sale del conteo vivo; una opción ausente
 * del conteo aparece con cero.
 */
export function fusionarFacetas(
  universo: FacetasCatalogo | null,
  conteos: FacetasCatalogo | null,
): FacetasCatalogo | null {
  if (!universo) return conteos;
  const numMarca = new Map((conteos?.marcas ?? []).map((m) => [m.id, m.conteo]));
  const numAtr = new Map(
    (conteos?.atributos ?? []).flatMap((a) =>
      a.valores.map((v) => [`${a.codigo}:${v.valor}`, v.conteo] as const),
    ),
  );
  return {
    total: conteos?.total ?? universo.total,
    disponibles: conteos?.disponibles ?? universo.disponibles,
    marcas: universo.marcas.map((m) => ({ ...m, conteo: numMarca.get(m.id) ?? 0 })),
    atributos: universo.atributos.map((a) => ({
      ...a,
      valores: a.valores.map((v) => ({
        ...v,
        conteo: numAtr.get(`${a.codigo}:${v.valor}`) ?? 0,
      })),
    })),
  };
}
