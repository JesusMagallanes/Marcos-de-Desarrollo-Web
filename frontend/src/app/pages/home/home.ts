import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import {
  BloqueCategoria,
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

  /*
   * Las tres listas llegan ya resueltas del servidor.
   *
   * Antes había una sola señal con el catálogo COMPLETO y de ella salían los
   * destacados, las ofertas y los bloques por categoría, filtrando y agrupando
   * aquí. Funcionaba, pero significaba descargar cada producto de la tienda
   * para enseñar unas decenas, y crecer con el catálogo aunque la pantalla
   * fuera siempre la misma.
   */
  protected destacados = signal<Producto[]>([]);
  protected enOferta = signal<Producto[]>([]);
  protected porCategoria = signal<BloqueCategoria[]>([]);
  /** Las 6 aprobadas mejor valoradas (más estrellas), para la sección de reseñas. */
  protected valoraciones = signal<ValoracionDestacada[]>([]);
  protected resenasChunks = computed(() => this.chunk(this.valoraciones(), 3));

  /**
   * Agrupación por categoría; sustituye a productosPorCategoria del
   * IndexController. La agrupación la hace el servidor; aquí solo se parte en
   * diapositivas para el carrusel, que es cosa de la vista.
   */
  protected bloques = computed<Bloque[]>(() =>
    this.porCategoria().map((b) => ({
      categoria: b.categoria,
      productos: b.productos,
      chunks: this.chunk(b.productos, 6),
    })),
  );

  /** "Productos Top": los diez que manda el servidor, en slides de 5. */
  protected topChunks = computed(() => this.chunk(this.destacados(), 5));

  /** Carrusel de ofertas, en slides de 6. */
  protected ofertaChunks = computed(() => this.chunk(this.enOferta(), 6));

  ngOnInit(): void {
    forkJoin({
      categorias: this.categoriaService.listar(),
      portada: this.productoService.portada(),
      valoraciones: this.valoracionService.destacadas(),
    }).subscribe({
      next: ({ categorias, portada, valoraciones }) => {
        this.categorias.set(categorias);
        this.destacados.set(portada.destacados);
        this.enOferta.set(portada.ofertas);
        this.porCategoria.set(portada.porCategoria);
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
