import { Cargando } from '../../shared/cargando/cargando';
import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Params, ParamMap, Router, RouterLink } from '@angular/router';
import {
  DescubrimientoService,
  FacetasCatalogo,
  FiltroCatalogo,
  OrdenCatalogo,
  Producto,
  ProductoService,
  filtroVacio,
  fusionarFacetas,
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
  selector: 'app-buscar',
  imports: [RouterLink, ProductoCard, Cargando, FiltrosCatalogo],
  templateUrl: './buscar.html',
  styleUrl: './buscar.css',
})
export class Buscar {
  private ruta = inject(ActivatedRoute);
  private router = inject(Router);
  private productoService = inject(ProductoService);
  private descubrimiento = inject(DescubrimientoService);

  private static readonly POR_PAGINA = 24;

  protected consulta = signal('');
  protected cargando = signal(true);
  protected resultados = signal<Producto[]>([]);
  protected paginaActual = signal(0);
  protected totalPaginas = signal(0);
  protected totalEncontrados = signal(0);

  protected filtro = signal<FiltroCatalogo>(filtroVacio());

  private universo = signal<FacetasCatalogo | null>(null);
  private conteos = signal<FacetasCatalogo | null>(null);
  protected facetas = computed(() => fusionarFacetas(this.universo(), this.conteos()));

  protected paginas = computed(() =>
    Array.from({ length: this.totalPaginas() }, (_, i) => i),
  );

  /** El último término que ya se contó como SEARCH, para no repetirlo. */
  private terminoResuelto: string | null = null;

  constructor() {
    this.ruta.queryParamMap.subscribe((q) => {
      const termino = q.get('q') ?? '';
      this.consulta.set(termino);
      this.filtro.set(this.leerFiltro(q));
      this.paginaActual.set(Math.max(0, +(q.get('page') ?? 0) || 0));

      // SEARCH se registra cuando cambia el término, no al cambiar un filtro:
      // filtrar dentro de la misma búsqueda no es una búsqueda nueva. El servicio
      // además descarta el término repetido.
      if (termino !== this.terminoResuelto) {
        this.terminoResuelto = termino;
        this.descubrimiento.busqueda(termino);
        this.universo.set(null);
        this.productoService.facetas({ buscar: termino }).subscribe({
          next: (u) => this.universo.set(u),
          error: () => this.universo.set(null),
        });
      }

      this.consultar(termino);
    });
  }

  private consultar(termino: string): void {
    const filtro = this.filtro();
    this.cargando.set(true);

    this.productoService.listar(termino, this.paginaActual(), Buscar.POR_PAGINA, filtro).subscribe({
      next: (pagina) => {
        this.resultados.set(pagina.content);
        this.totalPaginas.set(pagina.totalPages);
        this.totalEncontrados.set(pagina.totalElements);
        this.cargando.set(false);
      },
      error: () => {
        this.resultados.set([]);
        this.totalPaginas.set(0);
        this.totalEncontrados.set(0);
        this.cargando.set(false);
      },
    });

    this.productoService.facetas({ buscar: termino, filtro }).subscribe({
      next: (c) => this.conteos.set(c),
      error: () => this.conteos.set(null),
    });
  }

  /* ── Interacción: todo pasa por la URL ── */

  protected alFiltrar(nuevo: FiltroCatalogo): void {
    this.navegar(nuevo, 0);
  }

  protected alAtributo(evento: { codigo: string; valor: string }): void {
    this.descubrimiento.filtroAplicado(evento.codigo, evento.valor);
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
    // El término se conserva: los filtros afinan una búsqueda, no la sustituyen.
    if (this.consulta()) q['q'] = this.consulta();
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
