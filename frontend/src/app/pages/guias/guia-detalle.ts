import { Cargando } from '../../shared/cargando/cargando';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ErrorApi, EstadoPeticion, Guia, GuiaResumen, GuiaService, iconoDe } from '../../core';

/**
 * Una guía con sus pasos numerados.
 *
 * Los pasos llegan como filas separadas y se pintan por interpolación, sin
 * `innerHTML`: el texto lo escribe el administrador, pero aun así no se le
 * permite inyectar marcado en la página.
 */
@Component({
  selector: 'app-guia-detalle',
  imports: [RouterLink, Cargando],
  templateUrl: './guia-detalle.html',
  styleUrl: './guia-detalle.css',
})
export class GuiaDetalle implements OnInit, OnDestroy {
  private ruta = inject(ActivatedRoute);
  private guiaService = inject(GuiaService);

  protected estado = new EstadoPeticion();
  protected guia = signal<Guia | null>(null);
  protected otras = signal<GuiaResumen[]>([]);

  protected readonly iconoDe = iconoDe;

  ngOnInit(): void {
    // `paramMap` como observable y no una lectura única: al saltar de una guía
    // a otra desde el bloque final, Angular reutiliza el componente y sin esto
    // la página se quedaría con el contenido anterior.
    this.ruta.paramMap.subscribe((params) => {
      const slug = params.get('slug');
      if (slug) {
        this.cargar(slug);
      }
    });
  }

  ngOnDestroy(): void {
    this.estado.destruir();
  }

  private cargar(slug: string): void {
    this.estado.iniciar();
    this.guia.set(null);

    this.guiaService.obtener(slug).subscribe({
      next: (guia) => {
        this.guia.set(guia);
        this.estado.exito();
        window.scrollTo({ top: 0, behavior: 'auto' });
      },
      error: (e: ErrorApi) => this.estado.fallo(e),
    });

    this.guiaService.listar().subscribe({
      next: (todas) => this.otras.set(todas.filter((g) => g.slug !== slug).slice(0, 3)),
    });
  }
}
