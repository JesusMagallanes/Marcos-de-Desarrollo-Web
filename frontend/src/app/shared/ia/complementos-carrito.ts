import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Complementos, IaService, imagenDe } from '../../core';
import { ImagenCaida } from '../imagen/imagen-caida';

/**
 * «Completa tu compra»: accesorios para lo que hay en el carrito, con su porqué.
 *
 * Sugiere y enlaza a la ficha; no añade nada al carrito por su cuenta. Se vuelve
 * a pedir solo cuando cambia QUÉ hay en el carrito, no cuando cambia la cantidad.
 */
@Component({
  selector: 'app-complementos-carrito',
  imports: [RouterLink, ImagenCaida],
  template: `
    @if (complementos(); as c) {
      <section class="ia-bloque mt-3" aria-label="Completa tu compra">
        <div class="ia-bloque__titulo">
          <i class="fa-solid fa-wand-magic-sparkles" aria-hidden="true"></i> Completa tu compra
        </div>
        <div class="d-flex flex-column gap-2">
          @for (p of c.productos; track p.id) {
            <a class="complemento" [routerLink]="['/producto', p.id]">
              <img [src]="imagenDe(p)" [alt]="p.name" width="48" height="48" loading="lazy" />
              <div class="flex-grow-1 min-w-0">
                <div class="fw-semibold text-truncate">{{ p.name }}</div>
                <div class="small text-body-secondary">{{ c.motivos[p.id] }}</div>
              </div>
              <div class="fw-bold text-nowrap">S/ {{ p.precioActual }}</div>
            </a>
          }
        </div>
        <div class="ia-nota">Sugerencias con IA a partir de lo que llevas.</div>
      </section>
    }
  `,
  styleUrl: './ia-comun.css',
  styles: `
    .complemento {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.5rem;
      border-radius: 0.5rem;
      background: #fff;
      color: inherit;
      text-decoration: none;
      border: 1px solid #e3e8e5;
    }
    .complemento:hover {
      border-color: #06b56c;
    }
    .complemento img {
      object-fit: contain;
      flex-shrink: 0;
    }
  `,
})
export class ComplementosCarrito {
  readonly productoIds = input.required<number[]>();

  private readonly ia = inject(IaService);
  protected readonly imagenDe = imagenDe;
  protected readonly complementos = signal<Complementos | null>(null);
  private ultimaClave = '';

  constructor() {
    effect(() => {
      const ids = [...this.productoIds()].sort((a, b) => a - b);
      const disponible = this.ia.disponible();
      const clave = ids.join(',');
      untracked(() => {
        if (!disponible || !ids.length) {
          this.complementos.set(null);
          return;
        }
        if (clave === this.ultimaClave) return;
        this.ultimaClave = clave;
        this.ia.complementos(ids).subscribe((c) => this.complementos.set(c?.productos.length ? c : null));
      });
    });
  }
}
