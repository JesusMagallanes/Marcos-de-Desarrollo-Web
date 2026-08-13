import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ErrorApi, EstadoPeticion, GuiaResumen, GuiaService, iconoDe } from '../../core';

/**
 * Listado de guías de ayuda. Es lo que hay detrás de "Aprende con nosotros":
 * antes eran dos enlaces que no llevaban a ninguna parte.
 *
 * El contenido sale del backend, no del HTML: el administrador puede añadir,
 * reordenar o retirar guías sin tocar el código.
 */
@Component({
  selector: 'app-guias',
  imports: [RouterLink],
  templateUrl: './guias.html',
  styleUrl: './guias.css',
})
export class Guias implements OnInit, OnDestroy {
  private guiaService = inject(GuiaService);

  protected estado = new EstadoPeticion();
  protected guias = signal<GuiaResumen[]>([]);

  /** Se reexporta para el template, que no puede importar funciones sueltas. */
  protected readonly iconoDe = iconoDe;

  ngOnInit(): void {
    this.estado.iniciar();
    this.guiaService.listar().subscribe({
      next: (guias) => {
        this.guias.set(guias);
        this.estado.exito();
      },
      error: (e: ErrorApi) => this.estado.fallo(e),
    });
  }

  ngOnDestroy(): void {
    this.estado.destruir();
  }
}
