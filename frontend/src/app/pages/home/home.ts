import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import {
  AuthService,
  CarritoService,
  Categoria,
  CategoriaService,
  ErrorApi,
  Producto,
  ProductoService,
} from '../../core';
import { ProductoCard } from '../../shared/producto-card/producto-card';

interface Bloque {
  categoria: Categoria;
  productos: Producto[];
}

@Component({
  selector: 'app-home',
  imports: [RouterLink, ProductoCard],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class Home implements OnInit {
  private categoriaService = inject(CategoriaService);
  private productoService = inject(ProductoService);
  private carrito = inject(CarritoService);
  private auth = inject(AuthService);
  private router = inject(Router);

  protected cargando = signal(true);
  protected error = signal('');
  protected categorias = signal<Categoria[]>([]);
  protected productos = signal<Producto[]>([]);
  protected aviso = signal('');

  /** Agrupación por categoría; sustituye a productosPorCategoria del IndexController. */
  protected bloques = computed<Bloque[]>(() =>
    this.categorias()
      .map((categoria) => ({
        categoria,
        productos: this.productos()
          .filter((p) => p.categoriaId === categoria.id)
          .slice(0, 12),
      }))
      .filter((b) => b.productos.length > 0),
  );

  protected destacados = computed(() => this.productos().slice(0, 10));

  ngOnInit(): void {
    forkJoin({
      categorias: this.categoriaService.listar(),
      productos: this.productoService.listar(),
    }).subscribe({
      next: ({ categorias, productos }) => {
        this.categorias.set(categorias);
        this.productos.set(productos);
        this.cargando.set(false);
      },
      error: (e: ErrorApi) => {
        this.error.set(e.mensaje);
        this.cargando.set(false);
      },
    });
  }

  protected agregar(producto: Producto): void {
    if (!this.auth.autenticado()) {
      this.router.navigate(['/login'], { queryParams: { redirigir: '/' } });
      return;
    }
    this.carrito.agregar(producto.id, 1).subscribe({
      next: () => this.mostrarAviso(`"${producto.name}" se agregó al carrito.`),
      error: (e: ErrorApi) => this.mostrarAviso(e.mensaje),
    });
  }

  private mostrarAviso(texto: string): void {
    this.aviso.set(texto);
    setTimeout(() => this.aviso.set(''), 2800);
  }
}
