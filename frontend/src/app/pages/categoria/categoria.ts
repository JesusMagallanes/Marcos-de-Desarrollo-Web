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

  protected visibles = computed(() => {
    let lista = [...this.productos()];
    const marcas = this.marcasSeleccionadas();
    if (marcas.size > 0) lista = lista.filter((p) => p.marcaId !== null && marcas.has(p.marcaId));

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
