import { ImagenCaida } from '../imagen/imagen-caida';
import { Component, computed, input } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { IMAGEN_POR_DEFECTO, imagenDe, porcentajeDescuento, Producto } from '../../core';

@Component({
  selector: 'app-producto-card',
  imports: [RouterLink, ImagenCaida, CurrencyPipe],
  templateUrl: './producto-card.html',
  styleUrl: './producto-card.css',
})
export class ProductoCard {
  readonly producto = input.required<Producto>();
  /** 'carrusel' = tarjeta del home; 'grid' = .producto-card (categoría/búsqueda). */
  readonly variante = input<'carrusel' | 'grid'>('grid');

  protected readonly imagenDe = imagenDe;
  /** El marcador «Sin foto» no es una foto: sin sombra ni realce, que no parezca un cuadro flotando. */
  protected readonly sinFoto = computed(() => imagenDe(this.producto()) === IMAGEN_POR_DEFECTO);

  /** Porcentaje de descuento sobre el precio de lista, para la etiqueta «X% DSCTO». */
  protected readonly porcentaje = computed(() => porcentajeDescuento(this.producto()));
}
