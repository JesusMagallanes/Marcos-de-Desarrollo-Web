import { Cargando } from '../../shared/cargando/cargando';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Producto, ProductoService } from '../../core';
import { ProductoCard } from '../../shared/producto-card/producto-card';

@Component({
  selector: 'app-buscar',
  imports: [RouterLink, ProductoCard, Cargando],
  templateUrl: './buscar.html',
  styleUrl: './buscar.css',
})
export class Buscar {
  private ruta = inject(ActivatedRoute);
  private productoService = inject(ProductoService);

  /** Cuántos resultados se enseñan de una vez. */
  private static readonly POR_PAGINA = 24;

  protected consulta = signal('');
  protected cargando = signal(true);
  protected resultados = signal<Producto[]>([]);
  /** Cuántos hay en total; puede ser más de los que se enseñan. */
  protected totalEncontrados = signal(0);

  constructor() {
    this.ruta.queryParamMap.subscribe((q) => {
      const termino = q.get('q') ?? '';
      this.consulta.set(termino);
      this.buscar(termino);
    });
  }

  private buscar(termino: string): void {
    this.cargando.set(true);
    this.productoService.listar(termino, 0, Buscar.POR_PAGINA).subscribe({
      next: (pagina) => {
        this.resultados.set(pagina.content);
        this.totalEncontrados.set(pagina.totalElements);
        this.cargando.set(false);
      },
      error: () => {
        this.resultados.set([]);
        this.totalEncontrados.set(0);
        this.cargando.set(false);
      },
    });
  }
}
