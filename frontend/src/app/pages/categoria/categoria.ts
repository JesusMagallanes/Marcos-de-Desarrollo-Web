import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import {
  AuthService,
  CarritoService,
  Categoria as CategoriaModel,
  CategoriaService,
  DURACION_AVISO,
  ErrorApi,
  Marca,
  MarcaService,
  Producto,
  ProductoService,
} from '../../core';
import { ProductoCard } from '../../shared/producto-card/producto-card';

@Component({
  selector: 'app-categoria',
  imports: [RouterLink, ProductoCard],
  templateUrl: './categoria.html',
  styleUrl: './categoria.css',
})
export class Categoria {
  private ruta = inject(ActivatedRoute);
  private router = inject(Router);
  private categoriaService = inject(CategoriaService);
  private marcaService = inject(MarcaService);
  private productoService = inject(ProductoService);
  private carrito = inject(CarritoService);
  private auth = inject(AuthService);

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
  protected aviso = signal('');

  /* filtros */
  protected marcaSeleccionada = signal<number | null>(null);
  protected precioMax = signal<number | null>(null);
  protected orden = signal<'relevancia' | 'precio-asc' | 'precio-desc' | 'nombre'>('relevancia');

  protected visibles = computed(() => {
    let lista = [...this.productos()];
    const marca = this.marcaSeleccionada();
    if (marca !== null) lista = lista.filter((p) => p.marcaId === marca);

    const tope = this.precioMax();
    if (tope !== null) lista = lista.filter((p) => p.precio <= tope);

    switch (this.orden()) {
      case 'precio-asc':
        lista.sort((a, b) => a.precio - b.precio);
        break;
      case 'precio-desc':
        lista.sort((a, b) => b.precio - a.precio);
        break;
      case 'nombre':
        lista.sort((a, b) => a.name.localeCompare(b.name));
        break;
    }
    return lista;
  });

  protected paginas = computed(() => Array.from({ length: this.totalPaginas() }, (_, i) => i));

  constructor() {
    // Recarga cuando cambia el slug de la ruta.
    this.ruta.paramMap.subscribe(() => {
      this.paginaActual.set(0);
      this.marcaSeleccionada.set(null);
      this.precioMax.set(null);
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

  protected limpiarFiltros(): void {
    this.marcaSeleccionada.set(null);
    this.precioMax.set(null);
    this.orden.set('relevancia');
  }

  protected agregar(producto: Producto): void {
    if (!this.auth.autenticado()) {
      this.router.navigate(['/login'], { queryParams: { redirigir: this.router.url } });
      return;
    }
    this.carrito.agregar(producto.id, 1).subscribe({
      next: () => {
        this.aviso.set(`"${producto.name}" se agregó al carrito.`);
        setTimeout(() => this.aviso.set(''), 2800);
      },
      error: (e: ErrorApi) => {
        // El backend explica el motivo real ("Solo quedan 2 unidades de X").
        this.aviso.set(e.mensaje);
        setTimeout(() => this.aviso.set(''), DURACION_AVISO);
      },
    });
  }
}
