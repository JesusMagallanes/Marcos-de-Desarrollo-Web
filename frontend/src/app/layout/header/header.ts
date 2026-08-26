import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CurrencyPipe } from '@angular/common';
import { debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';
import {
  AuthService,
  CarritoItem,
  CarritoService,
  Categoria,
  CategoriaService,
  ENVIO,
  Producto,
  ProductoService,
  iconoCategoria,
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

  protected readonly iconoCategoria = iconoCategoria;
  protected readonly umbralEnvioGratis = ENVIO.umbralGratis;

  /** Cuántas sugerencias caben en el desplegable. */
  private static readonly MAX_SUGERENCIAS = 6;

  /**
   * Búsqueda incremental, resuelta en el servidor.
   *
   * <p>Antes el header se traía el CATÁLOGO COMPLETO a memoria y filtraba aquí
   * —«como hacía el header viejo», decía el comentario— y el header está en
   * todas las páginas: era una descarga del catálogo entero en cada visita, para
   * alimentar un desplegable de seis líneas.
   *
   * <p>`debounceTime` es lo que hace que esto no sea una petición por tecla:
   * espera a que el usuario pare de escribir. `distinctUntilChanged` evita
   * repetir la consulta cuando el texto vuelve a ser el mismo (borrar y
   * reescribir la última letra), y por debajo el interceptor de caché atiende
   * de memoria las búsquedas que ya se hicieron.
   */
  protected resultados = toSignal(
    toObservable(this.consulta).pipe(
      debounceTime(250),
      distinctUntilChanged(),
      switchMap((texto) => {
        const q = texto.trim();
        if (q.length < 2) {
          return of([] as Producto[]);
        }
        return this.productoService
          .listar(q, 0, Header.MAX_SUGERENCIAS)
          .pipe(switchMap((pagina) => of(pagina.content)));
      }),
    ),
    { initialValue: [] as Producto[] },
  );

  protected sugerencias = computed(() => {
    const vistos = new Set<string>();
    for (const p of this.resultados()) {
      vistos.add(p.name);
      if (p.marcaName) vistos.add(p.marcaName);
    }
    return [...vistos].slice(0, Header.MAX_SUGERENCIAS);
  });

  ngOnInit(): void {
    this.categoriaService.listar().subscribe({
      next: (cats) => this.categorias.set(cats),
      error: () => this.categorias.set([]),
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

  protected cambiarCantidad(item: CarritoItem, delta: number): void {
    const nueva = item.cantidad + delta;
    if (nueva < 1) {
      this.quitarItem(item.itemId);
    } else if (nueva <= item.stockDisponible) {
      this.carrito.cambiarCantidad(item.itemId, nueva).subscribe();
    }
  }
}
