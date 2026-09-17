import { Component, computed, inject } from '@angular/core';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map } from 'rxjs';
import { ComparacionService, MAX_COMPARAR, imagenDe } from '../../core';
import { ImagenCaida } from '../../shared/imagen/imagen-caida';

/**
 * La barra flotante con los productos elegidos para comparar.
 *
 * Aparece en cualquier pantalla de la tienda en cuanto hay uno, y se esconde en
 * la propia página de comparación, donde ya se está viendo lo mismo.
 */
@Component({
  selector: 'app-barra-comparacion',
  imports: [RouterLink, ImagenCaida],
  template: `
    @if (visible()) {
      <aside class="barra-comparacion" aria-label="Productos para comparar">
        <div class="d-flex align-items-center gap-2 flex-wrap">
          @for (p of comparacion.productos(); track p.id) {
            <div class="barra-comparacion__item">
              <img [src]="imagenDe(p)" [alt]="p.name" width="36" height="36" />
              <span class="text-truncate">{{ p.name }}</span>
              <button type="button" class="btn btn-sm btn-link text-secondary p-0" [attr.aria-label]="'Quitar ' + p.name"
                (click)="comparacion.quitar(p.id)">
                <i class="fa-solid fa-xmark" aria-hidden="true"></i>
              </button>
            </div>
          }
          <span class="small text-body-secondary">{{ comparacion.productos().length }}/{{ maximo }}</span>
        </div>
        <div class="d-flex gap-2">
          <button type="button" class="btn btn-sm btn-outline-secondary" (click)="comparacion.vaciar()">Vaciar</button>
          <a class="btn btn-sm btn-success" [class.disabled]="comparacion.productos().length < 2"
            [routerLink]="['/comparar']" [queryParams]="{ ids: comparacion.ids().join(',') }">
            Comparar
          </a>
        </div>
      </aside>
    }
  `,
  styles: `
    .barra-comparacion {
      position: fixed;
      left: 50%;
      bottom: 1rem;
      transform: translateX(-50%);
      z-index: 1040;
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 0.75rem;
      width: min(760px, calc(100% - 2rem));
      padding: 0.6rem 0.8rem;
      border: 1px solid #cdeedd;
      border-radius: 0.75rem;
      background: #fff;
      box-shadow: 0 8px 24px rgb(0 0 0 / 0.15);
    }
    .barra-comparacion__item {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      max-width: 200px;
      font-size: 0.8rem;
    }
    .barra-comparacion__item img {
      object-fit: contain;
      flex-shrink: 0;
    }
  `,
})
export class BarraComparacion {
  protected readonly comparacion = inject(ComparacionService);
  private readonly router = inject(Router);
  protected readonly imagenDe = imagenDe;
  protected readonly maximo = MAX_COMPARAR;

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  protected readonly visible = computed(
    () => this.comparacion.productos().length > 0 && !this.url().startsWith('/comparar'),
  );
}
