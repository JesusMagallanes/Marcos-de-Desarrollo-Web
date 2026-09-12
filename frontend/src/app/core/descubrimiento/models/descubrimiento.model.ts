import { Producto } from '../../catalogo/models';

/**
 * Lo que un sujeto puede hacer y que dice algo de lo que le interesa.
 *
 * <p>Espejo del enum del backend. No todos se emiten desde aquí: `FAVORITE` y
 * `PURCHASE` esperan a que existan esas pantallas, y `IMPRESSION` va por su
 * propio endpoint porque el volumen es otro.
 */
export type TipoEvento =
  | 'ITEM_VIEW'
  | 'ITEM_VIEW_DEEP'
  | 'CATEGORY_VIEW'
  | 'SEARCH'
  | 'SEARCH_CLICK'
  | 'ATTRIBUTE_FILTER'
  | 'COMPARE'
  | 'SHARE'
  | 'FAVORITE'
  | 'ADD_TO_CART'
  | 'REMOVE_FROM_CART'
  | 'REVIEW'
  | 'PURCHASE'
  | 'DISMISS'
  | 'NOT_INTERESTED';

export type TipoItem = 'PRODUCTO' | 'NEGOCIO' | 'SERVICIO' | 'PUBLICACION';

/**
 * De dónde viene una recomendación.
 *
 * <p>Llega del backend y la interfaz lo respeta: presentar una tendencia
 * general como si fuera personal es lo que hace que un recomendador se sienta
 * falso, y cuando acierta por casualidad, vigilante.
 */
export type Origen = 'PERSONAL' | 'COHORTE' | 'GEO' | 'TENDENCIA' | 'EXPLORACION';

/** Un evento suelto. El sujeto NO viaja aquí: lo resuelve el servidor. */
export interface EventoRequest {
  tipo: TipoEvento;
  itemTipo?: TipoItem;
  itemId?: number;
  categoriaId?: number;
  dwellMs?: number;
  /** De qué módulo salió lo que se tocó; alimenta el CTR por carrusel. */
  origen?: string;
  posicion?: number;
  /** Solo en ATTRIBUTE_FILTER. */
  atributoCodigo?: string;
  atributoValor?: string;
  /** Solo en SEARCH. */
  termino?: string;
}

export interface LoteEventosRequest {
  sesionId: string;
  /** Ubigeo INEI de seis dígitos. Nunca coordenadas. */
  ubigeo?: string;
  eventos: EventoRequest[];
}

export interface ImpresionRequest {
  itemTipo: TipoItem;
  itemId: number;
  modulo: string;
  posicion?: number;
}

export interface IngestaResponse {
  sujetoId: string;
  /** La credencial que acompaña al identificador. Sin ella no vale. */
  firma: string;
  registrados: number;
  /** Impresiones que el servidor no pudo casar con nada servido. Debe ser 0. */
  descartados: number;
}

/**
 * Un carrusel ya resuelto por el backend.
 *
 * <p>`motivo` es texto redactado para leerse («Porque te interesa monitores»),
 * no una explicación técnica. La interfaz lo muestra tal cual.
 */
export interface Carrusel {
  modulo: string;
  titulo: string;
  origen: Origen;
  motivo: string | null;
  items: Producto[];
}

export interface HomeDescubrimiento {
  /** El identificador que el visitante anónimo debe conservar. */
  sujetoId: string;
  /**
   * La firma que lo acompaña.
   *
   * <p>El identificador dejó de valer solo: hay que devolver los dos. El
   * secreto que la produce vive únicamente en el backend.
   */
  firma: string;
  carruseles: Carrusel[];
}
