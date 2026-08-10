import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Producto, ProductoService } from '../../core';
import { ProductoCard } from '../../shared/producto-card/producto-card';

@Component({
  selector: 'app-buscar',
  imports: [RouterLink, ProductoCard],
  templateUrl: './buscar.html',
  styleUrl: './buscar.css',
})
export class Buscar {
  private ruta = inject(ActivatedRoute);
  private productoService = inject(ProductoService);

  protected consulta = signal('');
  protected cargando = signal(true);
  protected resultados = signal<Producto[]>([]);

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
}
