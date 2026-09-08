import { Cargando } from '../../shared/cargando/cargando';
import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import {
  Categoria as CategoriaModel,
  CategoriaService,
  Marca,
  MarcaService,
  Producto,
  ProductoService,
  iconoCategoria,
  DescubrimientoService,
} from '../../core';
import { ProductoCard } from '../../shared/producto-card/producto-card';

type Orden = 'relevancia' | 'nombre-asc' | 'nombre-desc' | 'precio-asc' | 'precio-desc';
type Disponibilidad = 'todas' | 'stock' | 'agotado';

@Component({
  selector: 'app-categoria',
  imports: [RouterLink, ProductoCard, Cargando],
  templateUrl: './categoria.html',
  styleUrl: './categoria.css',
})
export class Categoria {
  private ruta = inject(ActivatedRoute);
  private categoriaService = inject(CategoriaService);
  private marcaService = inject(MarcaService);
  private productoService = inject(ProductoService);
  private descubrimiento = inject(DescubrimientoService);

  private slug = toSignal(this.ruta.paramMap.pipe(map((p) => p.get('slug') ?? '')), {
    initialValue: '',
  });

  protected cargando = signal(true);
  protected noEncontrada = signal(false);
  protected categoria = signal<CategoriaModel | null>(null);
  protected productos = signal<Producto[]>([]);
  protected marcas = signal<Marca[]>([]);
  protected paginaActual = signal(0);
  protected totalPaginas = signal(0);
  protected totalElementos = signal(0);

  /* filtros */
  protected marcasSeleccionadas = signal<Set<number>>(new Set());
  protected disponibilidad = signal<Disponibilidad>('todas');
  protected precioMin = signal<number | null>(null);
  protected precioMax = signal<number | null>(null);
  protected orden = signal<Orden>('relevancia');

  /**
   * Filtros por caracteristica, construidos con lo que YA llego.
   *
   * <p>Los atributos viajan en cada producto (`producto_atributo` normalizado),
   * asi que las facetas salen de ahi sin ninguna peticion extra y sin ninguna
   * taxonomia paralela en el navegador: si manana se anade un atributo nuevo en
   * el panel, aparece aqui solo.
   *
   * <p>Solo se ofrecen las que discriminan de verdad: una caracteristica con un
   * unico valor en toda la categoria no filtra nada y solo ocupa sitio.
   */
  protected facetas = computed(() => {
    const porCodigo = new Map<string, { nombre: string; valores: Set<string> }>();
    for (const p of this.productos()) {
      for (const a of p.atributos ?? []) {
        const entrada = porCodigo.get(a.codigo) ?? { nombre: a.nombre, valores: new Set<string>() };
        entrada.valores.add(a.valor);
        porCodigo.set(a.codigo, entrada);
      }
    }
    return [...porCodigo.entries()]
      .filter(([, v]) => v.valores.size > 1)
      .map(([codigo, v]) => ({ codigo, nombre: v.nombre, valores: [...v.valores].sort() }));
  });

  /** Qué valor está activo por cada característica. */
  protected atributosSeleccionados = signal<Map<string, string>>(new Map());

  /**
   * Aplica o quita un filtro por característica.
   *
   * <p>Solo se registra ATTRIBUTE_FILTER al APLICAR, no al quitar: elegir «27
   * pulgadas» dice lo que le interesa; deseleccionarlo dice que dejo de
   * filtrar, no que le disguste.
   */
  protected alternarAtributo(codigo: string, valor: string): void {
    const actual = new Map(this.atributosSeleccionados());
    if (actual.get(codigo) === valor) {
      actual.delete(codigo);
    } else {
      actual.set(codigo, valor);
      this.descubrimiento.filtroAplicado(codigo, valor, this.categoria()?.id);
    }
    this.atributosSeleccionados.set(actual);
  }

  protected atributoActivo(codigo: string, valor: string): boolean {
    return this.atributosSeleccionados().get(codigo) === valor;
  }

  protected visibles = computed(() => {
    let lista = [...this.productos()];
    const marcas = this.marcasSeleccionadas();
    if (marcas.size > 0) lista = lista.filter((p) => p.marcaId !== null && marcas.has(p.marcaId));

    const atributos = this.atributosSeleccionados();
    if (atributos.size > 0) {
      lista = lista.filter((p) =>
        [...atributos.entries()].every(([codigo, valor]) =>
          (p.atributos ?? []).some((a) => a.codigo === codigo && a.valor === valor),
        ),
      );
    }

    const min = this.precioMin();
    if (min !== null) lista = lista.filter((p) => p.precio >= min);

    const max = this.precioMax();
    if (max !== null) lista = lista.filter((p) => p.precio <= max);

    switch (this.disponibilidad()) {
      case 'stock':
        lista = lista.filter((p) => p.stock > 0);
        break;
      case 'agotado':
        lista = lista.filter((p) => p.stock <= 0);
        break;
    }

    switch (this.orden()) {
      case 'nombre-asc':
        lista.sort((a, b) => a.name.localeCompare(b.name));
        break;
      case 'nombre-desc':
        lista.sort((a, b) => b.name.localeCompare(a.name));
        break;
      case 'precio-asc':
        lista.sort((a, b) => a.precio - b.precio);
        break;
      case 'precio-desc':
        lista.sort((a, b) => b.precio - a.precio);
        break;
    }
    return lista;
  });

  protected paginas = computed(() => Array.from({ length: this.totalPaginas() }, (_, i) => i));

  protected iconoCategoria(): string {
    return iconoCategoria(this.categoria() ?? { icono: null });
  }

  constructor() {
    // Recarga cuando cambia el slug de la ruta.
    this.ruta.paramMap.subscribe(() => {
      this.paginaActual.set(0);
      this.limpiarFiltros();
      this.cargar();
    });
  }

  protected cargar(): void {
    const slug = this.slug();
    if (!slug) return;

    this.cargando.set(true);
    this.noEncontrada.set(false);

    this.categoriaService.obtenerPorSlug(slug).subscribe({
      next: (cat) => {
        this.categoria.set(cat);
        this.marcaService.listarPorCategoria(cat.id).subscribe({
          next: (m) => this.marcas.set(m),
          // Sin marcas el filtro se oculta; no es motivo para romper la página.
          error: () => this.marcas.set([]),
        });
        // CATEGORY_VIEW con el id ya resuelto: la URL trae el slug, y el
        // backend razona por id para poder propagar por el arbol.
        this.descubrimiento.vistaDeCategoria(cat.id);
        this.cargarPagina();
      },
      error: () => {
        this.noEncontrada.set(true);
        this.cargando.set(false);
      },
    });
  }

  private cargarPagina(): void {
    this.productoService.listarPorCategoria(this.slug(), this.paginaActual(), 12).subscribe({
      next: (pagina) => {
        this.productos.set(pagina.content);
        this.totalPaginas.set(pagina.totalPages);
        this.totalElementos.set(pagina.totalElements);
        this.cargando.set(false);
      },
      error: () => {
        this.productos.set([]);
        this.cargando.set(false);
      },
    });
  }

  protected irAPagina(n: number): void {
    if (n < 0 || n >= this.totalPaginas()) return;
    this.paginaActual.set(n);
    this.cargarPagina();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  protected alternarMarca(id: number, activo: boolean): void {
    const set = new Set(this.marcasSeleccionadas());
    if (activo) set.add(id);
    else set.delete(id);
    this.marcasSeleccionadas.set(set);
  }

  protected alternarDisponibilidad(tipo: Exclude<Disponibilidad, 'todas'>, activo: boolean): void {
    this.disponibilidad.set(activo ? tipo : this.disponibilidad() === tipo ? 'todas' : this.disponibilidad());
  }

  protected limpiarFiltros(): void {
    this.marcasSeleccionadas.set(new Set());
    this.disponibilidad.set('todas');
    this.precioMin.set(null);
    this.precioMax.set(null);
    this.orden.set('relevancia');
  }
}
