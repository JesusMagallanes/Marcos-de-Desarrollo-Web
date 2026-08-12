import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { etiquetaDescuento, Producto } from '../../core';

@Component({
  selector: 'app-producto-card',
  imports: [RouterLink],
  templateUrl: './producto-card.html',
  styleUrl: './producto-card.css',
})
export class ProductoCard {
  readonly producto = input.required<Producto>();
  /** 'carrusel' = .card-producto (home); 'grid' = .producto-card (categoría/búsqueda). */
  readonly variante = input<'carrusel' | 'grid'>('grid');

  protected readonly etiquetaDescuento = etiquetaDescuento;
  protected readonly redondeadoPromedio = computed(() =>
    Math.round(this.producto().calificacionPromedio ?? 0),
  );
}
