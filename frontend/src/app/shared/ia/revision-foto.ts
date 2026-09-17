import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { IaService, RevisionFoto as Revision } from '../../core';

/**
 * Revisión con IA de la foto que se acaba de subir.
 *
 * Se lanza sola cuando cambia la URL —la de una foto recién subida a la
 * tienda—, porque es justo el momento en que el vendedor puede cambiarla. Solo
 * avisa: no bloquea la subida ni el guardado, y la moderación sigue siendo de
 * una persona.
 */
@Component({
  selector: 'app-revision-foto',
  template: `
    @if (cargando()) {
      <div class="small text-body-secondary mt-1">
        <span class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span> Revisando la foto…
      </div>
    } @else if (revision(); as r) {
      <div class="small mt-1" role="status">
        @if (r.coincide === false) {
          <div class="text-danger"><i class="fa-solid fa-triangle-exclamation me-1" aria-hidden="true"></i>La foto no parece de este producto.</div>
        }
        @for (a of r.avisos; track a) {
          <div class="text-warning-emphasis"><i class="fa-solid fa-circle-info me-1" aria-hidden="true"></i>{{ a }}</div>
        }
        @if (r.coincide !== false && !r.avisos.length) {
          <div class="text-success"><i class="fa-solid fa-check me-1" aria-hidden="true"></i>La foto se ve bien.</div>
        }
      </div>
    }
  `,
})
export class RevisionFoto {
  readonly url = input<string | null>(null);
  readonly nombre = input('');

  private readonly ia = inject(IaService);
  protected readonly cargando = signal(false);
  protected readonly revision = signal<Revision | null>(null);

  constructor() {
    effect(() => {
      const url = this.url();
      untracked(() => this.revisar(url));
    });
  }

  private revisar(url: string | null): void {
    this.revision.set(null);
    if (!url || !this.ia.disponible() || !url.startsWith('/api/productos/imagenes/')) {
      return;
    }
    this.cargando.set(true);
    this.ia.revisarFoto(url, this.nombre().trim() || 'producto').subscribe((r) => {
      this.revision.set(r);
      this.cargando.set(false);
    });
  }
}
