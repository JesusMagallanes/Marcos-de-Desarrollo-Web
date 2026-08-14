import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import {
  Categoria,
  CategoriaService,
  ErrorApi,
  Producto,
  ProductoService,
  ValoracionDestacada,
  ValoracionService,
} from '../../core';
import { ProductoCard } from '../../shared/producto-card/producto-card';

interface Bloque {
  categoria: Categoria;
  productos: Producto[];
  chunks: Producto[][];
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
  private valoracionService = inject(ValoracionService);

  protected cargando = signal(true);
  protected error = signal('');
  protected categorias = signal<Categoria[]>([]);
  protected productos = signal<Producto[]>([]);
  /** Las 6 aprobadas mejor valoradas (más estrellas), para la sección de reseñas. */
  protected valoraciones = signal<ValoracionDestacada[]>([]);
  protected resenasChunks = computed(() => this.chunk(this.valoraciones(), 3));

  /** Agrupación por categoría; sustituye a productosPorCategoria del IndexController. */
  protected bloques = computed<Bloque[]>(() =>
    this.categorias()
      .map((categoria) => {
        const productos = this.productos()
          .filter((p) => p.categoriaId === categoria.id)
          .slice(0, 12);
        return { categoria, productos, chunks: this.chunk(productos, 6) };
      })
      .filter((b) => b.productos.length > 0),
  );

  /** "Productos Top": primeros 10, en slides de 5 + tarjeta promocional. */
  protected destacados = computed(() => this.productos().slice(0, 10));
  protected topChunks = computed(() => this.chunk(this.destacados(), 5));

  /** Productos con descuento vigente, para el carrusel de ofertas. */
  protected enOferta = computed(() => this.productos().filter((p) => p.enOferta));
  protected ofertaChunks = computed(() => this.chunk(this.enOferta(), 6));

  ngOnInit(): void {
    forkJoin({
      categorias: this.categoriaService.listar(),
      productos: this.productoService.listar(),
      valoraciones: this.valoracionService.destacadas(),
    }).subscribe({
      next: ({ categorias, productos, valoraciones }) => {
        this.categorias.set(categorias);
        this.productos.set(productos);
        this.valoraciones.set(valoraciones);
        this.cargando.set(false);
      },
      error: (e: ErrorApi) => {
        this.error.set(e.mensaje);
        this.cargando.set(false);
      },
    });
  }

  /** Imagen del producto reseñado, con fallback a la imagen por defecto. */
  imagenProducto(valoracion: ValoracionDestacada): string {
    return valoracion.productoImagenUrl || '/Img/img.png';
  }

  private chunk<T>(elementos: T[], tamaño: number): T[][] {
    const trozos: T[][] = [];
    for (let i = 0; i < elementos.length; i += tamaño) {
      trozos.push(elementos.slice(i, i + tamaño));
    }
    return trozos;
  }
}
