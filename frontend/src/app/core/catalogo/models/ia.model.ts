import { OrdenCatalogo, Producto } from './producto.model';

/** IA aplicada — servicio `catalogo` (:8081). Espejo de `IaDtos.java`. */

/**
 * Una búsqueda escrita como se habla, ya convertida en filtros que existen.
 *
 * `resumen` lo compone el servidor con lo que de verdad se aplicó, así que se
 * puede enseñar tal cual.
 */
export interface BusquedaInterpretada {
  texto: string | null;
  categoriaSlug: string | null;
  categoriaNombre: string | null;
  marcaIds: number[];
  marcaNombres: string[];
  precioMin: number | null;
  precioMax: number | null;
  soloDisponibles: boolean;
  orden: OrdenCatalogo;
  atributos: string[];
  resumen: string;
}

export interface RedaccionRequest {
  nombre: string;
  categoriaId: number;
  marcaId: number | null;
  notas: string | null;
}

/** Borrador: rellena el formulario, y la persona lo revisa antes de guardar. */
export interface RedaccionSugerida {
  descripcion: string;
  especificaciones: string;
}

export type RiesgoModeracion = 'BAJO' | 'MEDIO' | 'ALTO';

/**
 * Pre-análisis para quien modera. Nunca aprueba ni rechaza.
 * `conIa: false` = solo corrieron las reglas (sin modelo o sin respuesta).
 */
export interface AnalisisModeracion {
  riesgo: RiesgoModeracion;
  motivos: string[];
  sugerencia: string;
  conIa: boolean;
}

export interface ResumenResenas {
  resumen: string;
  aFavor: string[];
  enContra: string[];
  /** Cuántas reseñas aprobadas se resumieron. */
  basadoEn: number;
}

/** Respuesta a una pregunta sobre un producto. `consta: false` = la ficha no lo dice. */
export interface RespuestaFicha {
  respuesta: string;
  consta: boolean;
}

export interface FilaComparacion {
  caracteristica: string;
  /** Un valor por producto, en el mismo orden que `Comparacion.productos`. */
  valores: string[];
}

/** La tabla sale de la base; `explicacion` y `recomendadoId` son de la IA (null sin IA). */
export interface Comparacion {
  productos: Producto[];
  filas: FilaComparacion[];
  explicacion: string | null;
  recomendadoId: number | null;
}

/** Accesorios que completan el carrito, con el motivo de cada uno (clave: id). */
export interface Complementos {
  productos: Producto[];
  motivos: Record<number, string>;
}

/** Preguntas para escribir la reseña; nunca el texto. */
export interface AyudaResena {
  preguntas: string[];
}

export interface RevisionFoto {
  /** null si el modelo no lo tiene claro. */
  coincide: boolean | null;
  avisos: string[];
  textoAlternativo: string;
}

export interface ComoCorregir {
  pasos: string[];
}

export interface BusquedaPorFoto {
  /** Lo que se vio en la foto, como palabras de búsqueda. */
  consulta: string;
  busqueda: BusquedaInterpretada | null;
}
