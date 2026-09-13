import { Cargando } from '../../shared/cargando/cargando';
import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Params, ParamMap, Router, RouterLink } from '@angular/router';
import { combineLatest } from 'rxjs';
import {
  Categoria as CategoriaModel,
  CategoriaService,
  FacetasCatalogo,
  FiltroCatalogo,
  OrdenCatalogo,
  Producto,
  ProductoService,
  filtroVacio,
  fusionarFacetas,
  iconoCategoria,
  DescubrimientoService,
} from '../../core';
import { FiltrosCatalogo } from '../../shared/filtros-catalogo/filtros-catalogo';
import { ProductoCard } from '../../shared/producto-card/producto-card';

const ORDENES: readonly OrdenCatalogo[] = [
  'relevancia',
  'precio-asc',
  'precio-desc',
  'nombre-asc',
  'nombre-desc',
  'novedad',
];

@Component({
  selector: 'app-categoria',
  imports: [RouterLink, ProductoCard, Cargando, FiltrosCatalogo],
  templateUrl: './categoria.html',
  styleUrl: './categoria.css',
})
export class Categoria {
  private ruta = inject(ActivatedRoute);
  private router = inject(Router);
  private categoriaService = inject(CategoriaService);
  private productoService = inject(ProductoService);
  private descubrimiento = inject(DescubrimientoService);

  private static readonly POR_PAGINA = 12;

  /** El primer arranque tapa toda la página; un cambio de filtro, solo la rejilla. */
  protected cargandoCategoria = signal(true);
  protected cargandoProductos = signal(true);
  protected noEncontrada = signal(false);
  protected categoria = signal<CategoriaModel | null>(null);
  protected productos = signal<Producto[]>([]);
  protected paginaActual = signal(0);
  protected totalPaginas = signal(0);
  protected totalElementos = signal(0);

  protected filtro = signal<FiltroCatalogo>(filtroVacio());

  /**
   * El universo de opciones (sin filtrar) y los conteos del conjunto ya
   * filtrado, fusionados. El universo se lee una vez por categoría para que el
   * multi-select no se quede sin opciones al elegir la primera.
   */
  private universo = signal<FacetasCatalogo | null>(null);
  private conteos = signal<FacetasCatalogo | null>(null);
  protected facetas = computed(() => fusionarFacetas(this.universo(), this.conteos()));

  protected paginas = computed(() =>
    Array.from({ length: this.totalPaginas() }, (_, i) => i),
  );

  protected iconoCategoria(): string {
    return iconoCategoria(this.categoria() ?? { icono: null });
  }

  /** La categoría cuyo universo ya se cargó, para no repetirlo en cada filtro. */
  private slugResuelto = '';

  constructor() {
    // La URL manda: slug (ruta) + filtros/página (query). Se leen juntos para
    // no cargar dos veces al entrar, y para que atrás/adelante y recargar
    // reconstruyan la vista exacta.
    combineLatest([this.ruta.paramMap, this.ruta.queryParamMap]).subscribe(([p, q]) => {
      const slug = p.get('slug') ?? '';
      this.filtro.set(this.leerFiltro(q));
      this.paginaActual.set(Math.max(0, +(q.get('page') ?? 0) || 0));
      this.cargar(slug);
    });
  }

  private cargar(slug: string): void {
    if (!slug) return;

    if (slug !== this.slugResuelto) {
      // Categoría nueva: resolverla, sembrar el universo de facetas y contar el
      // primer CATEGORY_VIEW. Solo aquí, no en cada cambio de filtro.
      this.slugResuelto = slug;
      this.cargandoCategoria.set(true);
      this.noEncontrada.set(false);
      this.universo.set(null);
      this.conteos.set(null);

      this.categoriaService.obtenerPorSlug(slug).subscribe({
        next: (cat) => {
          this.categoria.set(cat);
          this.descubrimiento.vistaDeCategoria(cat.id);
          this.cargandoCategoria.set(false);
          this.productoService.facetas({ slug }).subscribe({
            next: (u) => this.universo.set(u),
            error: () => this.universo.set(null),
          });
          this.consultar(slug);
        },
        error: () => {
          this.noEncontrada.set(true);
          this.cargandoCategoria.set(false);
        },
      });
      return;
    }

    this.consultar(slug);
  }

  /** Trae la página filtrada y los conteos del mismo conjunto. */
  private consultar(slug: string): void {
    const filtro = this.filtro();
    this.cargandoProductos.set(true);

    this.productoService
      .listarPorCategoria(slug, this.paginaActual(), Categoria.POR_PAGINA, filtro)
      .subscribe({
        next: (pagina) => {
          this.productos.set(pagina.content);
          this.totalPaginas.set(pagina.totalPages);
          this.totalElementos.set(pagina.totalElements);
          this.cargandoProductos.set(false);
        },
        error: () => {
          this.productos.set([]);
          this.totalPaginas.set(0);
          this.totalElementos.set(0);
          this.cargandoProductos.set(false);
        },
      });

    this.productoService.facetas({ slug, filtro }).subscribe({
      next: (c) => this.conteos.set(c),
      error: () => this.conteos.set(null),
    });
  }

  /* ── Interacción: todo pasa por la URL ── */

  protected alFiltrar(nuevo: FiltroCatalogo): void {
    // Cambiar un filtro vuelve a la primera página: mantener la 4 al recortar a
    // dos resultados dejaría la vista vacía sin que el usuario entienda por qué.
    this.navegar(nuevo, 0);
  }

  protected alAtributo(evento: { codigo: string; valor: string }): void {
    this.descubrimiento.filtroAplicado(evento.codigo, evento.valor, this.categoria()?.id);
  }

  protected irAPagina(n: number): void {
    if (n < 0 || n >= this.totalPaginas()) return;
    this.navegar(this.filtro(), n);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  private navegar(filtro: FiltroCatalogo, page: number): void {
    this.router.navigate([], {
      relativeTo: this.ruta,
      queryParams: this.aQuery(filtro, page),
    });
  }

  /* ── URL ⇆ filtro ── */

  private aQuery(f: FiltroCatalogo, page: number): Params {
    const q: Params = {};
    if (f.precioMin != null) q['precioMin'] = f.precioMin;
    if (f.precioMax != null) q['precioMax'] = f.precioMax;
    if (f.marcaIds.length) q['marca'] = f.marcaIds;
    if (f.atributos.length) q['atr'] = f.atributos;
    if (f.soloDisponibles) q['disp'] = 1;
    if (f.orden !== 'relevancia') q['orden'] = f.orden;
    if (page > 0) q['page'] = page;
    return q;
  }

  private leerFiltro(q: ParamMap): FiltroCatalogo {
    const num = (clave: string): number | null => {
      const v = q.get(clave);
      return v != null && v !== '' && !isNaN(+v) ? +v : null;
    };
    const orden = q.get('orden');
    return {
      precioMin: num('precioMin'),
      precioMax: num('precioMax'),
      marcaIds: q
        .getAll('marca')
        .map(Number)
        .filter((n) => Number.isFinite(n)),
      atributos: q.getAll('atr').filter((a) => a.includes(':')),
      soloDisponibles: q.get('disp') === '1',
      orden: ORDENES.includes(orden as OrdenCatalogo) ? (orden as OrdenCatalogo) : 'relevancia',
    };
  }
}
