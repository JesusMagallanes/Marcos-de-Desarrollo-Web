/** Servicio `catalogo` (:8081). Una marca pertenece a una categoría. */
export interface Marca {
  id: number;
  name: string;
  descripcion: string;
  /** Id plano; el objeto Categoria no viaja anidado. */
  categoriaId: number;
}

export interface MarcaRequest {
  name: string;
  descripcion: string;
  categoriaId: number;
}
