import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { etiquetaDescuento, imagenDe, porcentajeDescuento, Producto } from '../../core';

@Component({
  selector: 'app-producto-card',
  imports: [RouterLink],
  templateUrl: './producto-card.html',
  styleUrl: './producto-card.css',
})
export class ProductoCard {
  readonly producto = input.required<Producto>();
  /** 'carrusel' = tarjeta del home; 'grid' = .producto-card (categoría/búsqueda). */
  readonly variante = input<'carrusel' | 'grid'>('grid');

  protected readonly etiquetaDescuento = etiquetaDescuento;
  protected readonly imagenDe = imagenDe;
  protected readonly redondeadoPromedio = computed(() =>
    Math.round(this.producto().calificacionPromedio ?? 0),
  );

  /** Porcentaje de descuento sobre el precio de lista, para la etiqueta «X% DSCTO». */
  protected readonly porcentaje = computed(() => porcentajeDescuento(this.producto()));
}
