import { Cargando } from '../../shared/cargando/cargando';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DescubrimientoService, Producto, ProductoService } from '../../core';
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
  private descubrimiento = inject(DescubrimientoService);

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
    /*
     * SEARCH se registra cuando la busqueda YA SE EJECUTO, no al teclear.
     *
     * Este componente reacciona al parametro `q` de la URL, que solo cambia al
     * enviar el formulario, asi que aqui no llegan las pulsaciones sueltas
     * («m», «mo», «mon»...). El servicio ademas descarta el termino repetido,
     * de modo que recargar o volver atras no cuenta como una busqueda mas.
     */
    this.descubrimiento.busqueda(termino);

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
