import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CurrencyPipe } from '@angular/common';
import {
  AuthService,
  CarritoService,
  Categoria,
  CategoriaService,
  Producto,
  ProductoService,
} from '../../core';

@Component({
  selector: 'app-header',
  imports: [RouterLink, FormsModule, CurrencyPipe],
  templateUrl: './header.html',
  styleUrl: './header.css',
})
export class Header implements OnInit {
  private categoriaService = inject(CategoriaService);
  private productoService = inject(ProductoService);
  private router = inject(Router);
  protected auth = inject(AuthService);
  protected carrito = inject(CarritoService);

  protected categorias = signal<Categoria[]>([]);
  protected consulta = signal('');
  protected panelAbierto = signal(false);

  /** Catálogo completo en memoria para la búsqueda incremental, como hacía el header viejo. */
  private todos = signal<Producto[]>([]);

  protected resultados = computed(() => {
    const q = this.consulta().trim().toLowerCase();
    if (q.length < 2) return [];
    return this.todos()
      .filter(
        (p) =>
          p.name?.toLowerCase().includes(q) || p.marcaName?.toLowerCase().includes(q),
      )
      .slice(0, 6);
  });

  protected sugerencias = computed(() => {
    const vistos = new Set<string>();
    for (const p of this.resultados()) {
      vistos.add(p.name);
      if (p.marcaName) vistos.add(p.marcaName);
    }
    return [...vistos].slice(0, 6);
  });

  ngOnInit(): void {
    this.categoriaService.listar().subscribe({
      next: (cats) => this.categorias.set(cats),
      error: () => this.categorias.set([]),
    });
    this.productoService.listar().subscribe({
      next: (prods) => this.todos.set(prods),
      error: () => this.todos.set([]),
    });
    this.carrito.refrescar();
  }

  protected buscar(event: Event): void {
    event.preventDefault();
    const q = this.consulta().trim();
    if (!q) return;
    this.panelAbierto.set(false);
    this.router.navigate(['/buscar'], { queryParams: { q } });
  }

  protected usarSugerencia(texto: string): void {
    this.consulta.set(texto);
    this.panelAbierto.set(true);
  }

  protected irAProducto(id: number): void {
    this.panelAbierto.set(false);
    this.consulta.set('');
    this.router.navigate(['/producto', id]);
  }

  protected cerrarSesion(): void {
    this.auth.logout();
  }

  /** El login ya es un modal global: se abre sin abandonar la página. */
  protected abrirLogin(): void {
    this.auth.abrirLogin({ redirigir: this.router.url });
  }

  protected quitarItem(itemId: number): void {
    this.carrito.eliminar(itemId).subscribe();
  }
}
