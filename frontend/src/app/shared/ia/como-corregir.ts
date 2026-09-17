import { Component, inject, input, signal } from '@angular/core';
import { ErrorApi, IaService } from '../../core';

/**
 * «Cómo corregirlo»: convierte el motivo de rechazo de un producto propio en
 * pasos concretos. No cambia la decisión del moderador ni promete aprobarlo.
 */
@Component({
  selector: 'app-como-corregir',
  template: `
    @if (ia.disponible()) {
      @if (pasos().length) {
        <ol class="small mb-0 mt-1 ps-3">
          @for (p of pasos(); track p) {
            <li>{{ p }}</li>
          }
        </ol>
      } @else {
        <button type="button" class="btn btn-sm btn-link text-success p-0" [disabled]="cargando()" (click)="pedir()">
          <i class="fa-solid fa-wand-magic-sparkles me-1" aria-hidden="true"></i>
          {{ cargando() ? 'Pensando…' : '¿Cómo lo corrijo?' }}
        </button>
        @if (error()) {
          <span class="small text-body-secondary ms-1">{{ error() }}</span>
        }
      }
    }
  `,
})
export class ComoCorregir {
  readonly productoId = input.required<number>();

  protected readonly ia = inject(IaService);
  protected readonly cargando = signal(false);
  protected readonly pasos = signal<string[]>([]);
  protected readonly error = signal('');

  protected pedir(): void {
    this.cargando.set(true);
    this.error.set('');
    this.ia.comoCorregir(this.productoId()).subscribe({
      next: (r) => {
        this.cargando.set(false);
        if (r?.pasos.length) {
          this.pasos.set(r.pasos);
        } else {
          this.error.set('Ahora no puedo ayudarte con esto.');
        }
      },
      error: (e: ErrorApi) => {
        this.cargando.set(false);
        this.error.set(e.mensaje);
      },
    });
  }
}
