import { Cargando } from '../../../shared/cargando/cargando';
import { Component, OnInit, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  CLASE_ESTADO_PEDIDO,
  ErrorApi,
  EstadoPedido,
  ETIQUETA_ESTADO_PEDIDO,
  Pedido,
  PedidoService,
} from '../../../core';

@Component({
  selector: 'app-mis-compras',
  imports: [CurrencyPipe, DatePipe, RouterLink, Cargando],
  templateUrl: './mis-compras.html',
  styleUrl: './mis-compras.css',
})
export class MisCompras implements OnInit {
  private pedidoService = inject(PedidoService);
  private ruta = inject(ActivatedRoute);

  protected cargando = signal(true);
  protected error = signal('');
  protected lista = signal<Pedido[]>([]);
  protected expandido = signal<number | null>(null);
  protected recienCreado = signal<number | null>(null);

  ngOnInit(): void {
    const nuevo = this.ruta.snapshot.queryParamMap.get('nuevo');
    if (nuevo) {
      this.recienCreado.set(+nuevo);
      this.expandido.set(+nuevo);
    }

    this.pedidoService.mios().subscribe({
      next: (p) => {
        this.lista.set(p);
        this.cargando.set(false);
      },
      error: (e: ErrorApi) => {
        this.error.set(e.mensaje);
        this.cargando.set(false);
      },
    });
  }

  protected alternar(id: number): void {
    this.expandido.set(this.expandido() === id ? null : id);
  }

  // Etiquetas y clases viven en pedido.model.ts: la vista solo las consulta.
  protected claseEstado(estado: EstadoPedido): string {
    return CLASE_ESTADO_PEDIDO[estado];
  }

  protected etiquetaEstado(estado: EstadoPedido): string {
    return ETIQUETA_ESTADO_PEDIDO[estado];
  }
}
