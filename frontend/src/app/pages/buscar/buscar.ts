import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService, CarritoService, Producto, ProductoService } from '../../core';
import { ProductoCard } from '../../shared/producto-card/producto-card';

@Component({
  selector: 'app-buscar',
  imports: [RouterLink, ProductoCard],
  templateUrl: './buscar.html',
  styleUrl: './buscar.css',
})
export class Buscar {
  private ruta = inject(ActivatedRoute);
  private router = inject(Router);
  private productoService = inject(ProductoService);
  private carrito = inject(CarritoService);
  private auth = inject(AuthService);

  protected consulta = signal('');
  protected cargando = signal(true);
  protected resultados = signal<Producto[]>([]);
  protected aviso = signal('');

  constructor() {
    this.ruta.queryParamMap.subscribe((q) => {
      const termino = q.get('q') ?? '';
      this.consulta.set(termino);
      this.buscar(termino);
    });
  }

  private buscar(termino: string): void {
    this.cargando.set(true);
    this.productoService.listar(termino).subscribe({
      next: (p) => {
        this.resultados.set(p);
        this.cargando.set(false);
      },
      error: () => {
        this.resultados.set([]);
        this.cargando.set(false);
      },
    });
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
    });
  }
}
