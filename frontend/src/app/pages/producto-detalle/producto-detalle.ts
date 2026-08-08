import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CurrencyPipe } from '@angular/common';
import { AuthService, CarritoService, ErrorApi, Producto, ProductoService } from '../../core';
import { ProductoCard } from '../../shared/producto-card/producto-card';

@Component({
  selector: 'app-producto-detalle',
  imports: [RouterLink, CurrencyPipe, ProductoCard],
  templateUrl: './producto-detalle.html',
  styleUrl: './producto-detalle.css',
})
export class ProductoDetalle {
  private ruta = inject(ActivatedRoute);
  private router = inject(Router);
  private productoService = inject(ProductoService);
  private carrito = inject(CarritoService);
  private auth = inject(AuthService);

  protected cargando = signal(true);
  protected noEncontrado = signal(false);
  protected producto = signal<Producto | null>(null);
  protected relacionados = signal<Producto[]>([]);
  protected cantidad = signal(1);
  protected aviso = signal('');

  protected agotado = computed(() => (this.producto()?.stock ?? 0) <= 0);
  protected maximo = computed(() => Math.max(1, this.producto()?.stock ?? 1));

  constructor() {
    this.ruta.paramMap.subscribe((params) => {
      const id = Number(params.get('id'));
      if (!id) {
        this.noEncontrado.set(true);
        this.cargando.set(false);
        return;
      }
      this.cargar(id);
    });
  }

  private cargar(id: number): void {
    this.cargando.set(true);
    this.noEncontrado.set(false);
    this.cantidad.set(1);

    this.productoService.obtener(id).subscribe({
      next: (p) => {
        this.producto.set(p);
        this.cargando.set(false);
        this.cargarRelacionados(p);
        window.scrollTo({ top: 0 });
      },
      error: () => {
        this.noEncontrado.set(true);
        this.cargando.set(false);
      },
    });
  }

  private cargarRelacionados(actual: Producto): void {
    this.productoService.listar().subscribe({
      next: (todos) =>
        this.relacionados.set(
          todos.filter((p) => p.categoriaId === actual.categoriaId && p.id !== actual.id).slice(0, 6),
        ),
      // Los relacionados son accesorios: si fallan, la ficha sigue siendo útil.
      error: () => this.relacionados.set([]),
    });
  }

  protected cambiarCantidad(delta: number): void {
    const nueva = this.cantidad() + delta;
    if (nueva < 1 || nueva > this.maximo()) return;
    this.cantidad.set(nueva);
  }

  protected agregarAlCarrito(): void {
    const p = this.producto();
    if (!p || this.agotado()) return;

    if (!this.auth.autenticado()) {
      this.router.navigate(['/login'], { queryParams: { redirigir: `/producto/${p.id}` } });
      return;
    }

    this.carrito.agregar(p.id, this.cantidad()).subscribe({
      next: () => this.mostrarAviso('Producto agregado al carrito.'),
      error: (e: ErrorApi) => this.mostrarAviso(e.mensaje),
    });
  }

  protected comprarAhora(): void {
    const p = this.producto();
    if (!p || this.agotado()) return;

    if (!this.auth.autenticado()) {
      this.router.navigate(['/login'], { queryParams: { redirigir: `/producto/${p.id}` } });
      return;
    }

    this.carrito.agregar(p.id, this.cantidad()).subscribe({
      next: () => this.router.navigate(['/carrito']),
      error: (e: ErrorApi) => this.mostrarAviso(e.mensaje),
    });
  }

  protected agregarRelacionado(p: Producto): void {
    if (!this.auth.autenticado()) {
      this.router.navigate(['/login']);
      return;
    }
    this.carrito.agregar(p.id, 1).subscribe({
      next: () => this.mostrarAviso(`"${p.name}" se agregó al carrito.`),
      error: (e: ErrorApi) => this.mostrarAviso(e.mensaje),
    });
  }

  private mostrarAviso(texto: string): void {
    this.aviso.set(texto);
    setTimeout(() => this.aviso.set(''), 2800);
  }
}
