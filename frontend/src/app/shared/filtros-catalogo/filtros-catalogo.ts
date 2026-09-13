import { Component, computed, input, model, output } from '@angular/core';
import { FacetasCatalogo, FiltroCatalogo, OrdenCatalogo } from '../../core';

/** Cuántos rails se han creado; da un sufijo distinto a los `id` de cada uno. */
let instancias = 0;

/**
 * El rail de filtros, común a la búsqueda y a la categoría.
 *
 * <p>Antes cada página resolvía esto por su cuenta: la categoría filtraba en el
 * navegador sobre los doce productos cargados y la búsqueda no filtraba nada.
 * Ahora las dos comparten este componente y el trabajo lo hace el servidor;
 * aquí solo se pinta el estado y se avisa de cada cambio.
 *
 * <h4>Cómo comunica</h4>
 *
 * <p>El filtro es un {@link https://angular.dev/guide/signals/model | model}:
 * enlace en dos sentidos. Cada cambio produce un objeto NUEVO —no se muta el
 * anterior— para que el padre reaccione y vuelva a consultar. La única señal que
 * sale aparte es {@link atributoAplicado}, para que la categoría registre el
 * ATTRIBUTE_FILTER de descubrimiento sin que este componente sepa de métricas.
 */
@Component({
  selector: 'app-filtros-catalogo',
  imports: [],
  templateUrl: './filtros-catalogo.html',
  styleUrl: './filtros-catalogo.css',
})
export class FiltrosCatalogo {
  /**
   * Universo de opciones con sus conteos ya fusionados por el padre. Las
   * opciones son estables (no menguan al filtrar); el número «(7)» es del
   * conjunto ya filtrado.
   */
  readonly facetas = input<FacetasCatalogo | null>(null);

  /** El estado del filtro, en dos sentidos. */
  readonly filtro = model.required<FiltroCatalogo>();

  /** Solo al APLICAR un atributo (no al quitarlo): es la señal más específica. */
  readonly atributoAplicado = output<{ codigo: string; valor: string }>();

  /**
   * Sufijo para los `id` de los controles con etiqueta.
   *
   * <p>La categoría pinta este rail DOS veces —la columna de escritorio y el
   * panel lateral del móvil—, así que un `id` fijo se repetiría en la página y
   * cada etiqueta apuntaría al control de la otra copia.
   */
  protected readonly sufijo = `-${++instancias}`;

  protected readonly marcas = computed(() => this.facetas()?.marcas ?? []);
  protected readonly atributos = computed(() => this.facetas()?.atributos ?? []);

  protected readonly opciones: { valor: OrdenCatalogo; etiqueta: string }[] = [
    { valor: 'relevancia', etiqueta: 'Relevancia' },
    { valor: 'precio-asc', etiqueta: 'Precio: menor a mayor' },
    { valor: 'precio-desc', etiqueta: 'Precio: mayor a menor' },
    { valor: 'nombre-asc', etiqueta: 'Nombre: A-Z' },
    { valor: 'nombre-desc', etiqueta: 'Nombre: Z-A' },
    { valor: 'novedad', etiqueta: 'Novedad' },
  ];

  /** Cuántos filtros hay puestos, para el botón de limpiar. */
  protected readonly cuantosActivos = computed(() => {
    const f = this.filtro();
    return (
      f.marcaIds.length +
      f.atributos.length +
      (f.precioMin != null ? 1 : 0) +
      (f.precioMax != null ? 1 : 0) +
      (f.soloDisponibles ? 1 : 0)
    );
  });

  protected marcaActiva(id: number): boolean {
    return this.filtro().marcaIds.includes(id);
  }

  protected alternarMarca(id: number, activo: boolean): void {
    const f = this.filtro();
    const marcaIds = activo
      ? [...f.marcaIds, id]
      : f.marcaIds.filter((m) => m !== id);
    this.filtro.set({ ...f, marcaIds });
  }

  protected atributoActivo(codigo: string, valor: string): boolean {
    return this.filtro().atributos.includes(`${codigo}:${valor}`);
  }

  protected alternarAtributo(codigo: string, valor: string): void {
    const token = `${codigo}:${valor}`;
    const f = this.filtro();
    const puesto = f.atributos.includes(token);
    const atributos = puesto
      ? f.atributos.filter((a) => a !== token)
      : [...f.atributos, token];
    this.filtro.set({ ...f, atributos });
    if (!puesto) this.atributoAplicado.emit({ codigo, valor });
  }

  protected alternarDisponibles(activo: boolean): void {
    this.filtro.set({ ...this.filtro(), soloDisponibles: activo });
  }

  protected fijarPrecioMin(valor: string): void {
    this.filtro.set({ ...this.filtro(), precioMin: valor ? +valor : null });
  }

  protected fijarPrecioMax(valor: string): void {
    this.filtro.set({ ...this.filtro(), precioMax: valor ? +valor : null });
  }

  protected fijarOrden(valor: string): void {
    this.filtro.set({ ...this.filtro(), orden: valor as OrdenCatalogo });
  }

  protected limpiar(): void {
    this.filtro.set({
      precioMin: null,
      precioMax: null,
      marcaIds: [],
      atributos: [],
      soloDisponibles: false,
      // El orden es una preferencia de lectura, no un filtro: no se toca al
      // limpiar los filtros.
      orden: this.filtro().orden,
    });
  }
}
