import { Component, computed, inject, input, output } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService, Producto } from '../../core';

@Component({
  selector: 'app-producto-card',
  imports: [RouterLink, CurrencyPipe],
  templateUrl: './producto-card.html',
  styleUrl: './producto-card.css',
})
export class ProductoCard {
  readonly producto = input.required<Producto>();
  readonly agregar = output<Producto>();

  protected auth = inject(AuthService);
  protected agotado = computed(() => this.producto().stock <= 0);

  protected alAgregar(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    if (!this.agotado()) this.agregar.emit(this.producto());
  }
}
