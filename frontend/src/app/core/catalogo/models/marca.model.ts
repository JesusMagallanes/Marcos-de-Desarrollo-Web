/**
 * Servicio `catalogo` (:8081). Una marca vende en VARIAS categorías.
 *
 * Era una sola, y eso obligaba a dar de alta «LG», «LG (TV)» y «LG (Línea
 * blanca)» como marcas distintas. Ver `docs/modelo-datos.md` y la migración V18.
 */
export interface Marca {
  id: number;
  name: string;
  descripcion: string;
  /**
   * La primera de `categoriaIds`.
   * @deprecated Se mantiene mientras el panel siga enviando una sola. Usa `categoriaIds`.
   */
  categoriaId: number | null;
  /** Todas las categorías en las que vende. */
  categoriaIds: number[];
}

export interface MarcaRequest {
  name: string;
  descripcion: string;
  /** El backend acepta esta o `categoriaIds`; con una sola basta enviar esta. */
  categoriaId?: number;
  categoriaIds?: number[];
}
