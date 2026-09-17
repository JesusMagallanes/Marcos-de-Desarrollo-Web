import { Component, inject, input, output, signal } from '@angular/core';
import { IaService } from '../../core';

/**
 * «Ideas para tu reseña»: preguntas que ayudan a escribir, nunca el texto.
 *
 * Si la IA redactara la opinión dejaría de ser de quien compró. Aquí propone
 * preguntas («¿qué tal la batería?»); pulsar una la añade al final del borrador
 * como pie para que la persona conteste con sus palabras.
 */
@Component({
  selector: 'app-ayuda-resena',
  template: `
    @if (ia.disponible()) {
      <div class="d-flex flex-wrap align-items-center gap-1 mb-2">
        <button type="button" class="btn btn-sm btn-link text-success p-0 me-2" [disabled]="cargando()"
          (click)="pedir()">
          <i class="fa-solid fa-wand-magic-sparkles me-1" aria-hidden="true"></i>
          {{ cargando() ? 'Pensando…' : 'Ideas para tu reseña' }}
        </button>
        @for (p of preguntas(); track p) {
          <button type="button" class="btn btn-sm btn-light border ia-chip" (click)="usar.emit(p)">{{ p }}</button>
        }
      </div>
    }
  `,
  styleUrl: './ia-comun.css',
})
export class AyudaResena {
  readonly productoId = input.required<number>();
  readonly calificacion = input.required<number>();
  readonly borrador = input('');
  /** La pregunta elegida; quien usa el componente decide cómo añadirla. */
  readonly usar = output<string>();

  protected readonly ia = inject(IaService);
  protected readonly cargando = signal(false);
  protected readonly preguntas = signal<string[]>([]);

  protected pedir(): void {
    this.cargando.set(true);
    this.ia.ayudaResena(this.productoId(), this.calificacion() || 5, this.borrador()).subscribe((r) => {
      this.preguntas.set(r?.preguntas ?? []);
      this.cargando.set(false);
    });
  }
}
