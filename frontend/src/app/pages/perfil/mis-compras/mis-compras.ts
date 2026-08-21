import { Cargando } from '../../../shared/cargando/cargando';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import {
  CLASE_ESTADO_ENVIO,
  CLASE_ESTADO_PEDIDO,
  Envio,
  EnvioService,
  ErrorApi,
  ETIQUETA_ESTADO_ENVIO,
  ETIQUETA_ESTADO_PEDIDO,
  EstadoEnvio,
  EstadoPedido,
  Pedido,
  PedidoService,
  destinoCompleto,
  pagaAlRecibir,
} from '../../../core';

/** Un pedido con su envío, que es como el comprador lo entiende: una compra. */
interface CompraConEnvio {
  pedido: Pedido;
  /** `null` mientras el envío no exista todavía o no se haya podido cargar. */
  envio: Envio | null;
}

@Component({
  selector: 'app-mis-compras',
  imports: [CurrencyPipe, DatePipe, RouterLink, Cargando],
  templateUrl: './mis-compras.html',
  styleUrl: './mis-compras.css',
})
export class MisCompras implements OnInit {
  private pedidoService = inject(PedidoService);
  private envioService = inject(EnvioService);
  private ruta = inject(ActivatedRoute);

  protected cargando = signal(true);
  protected error = signal('');
  protected compras = signal<CompraConEnvio[]>([]);
  protected expandido = signal<number | null>(null);
  protected recienCreado = signal<number | null>(null);

  /** El número del pedido recién hecho, para el mensaje de gracias. */
  protected numeroRecienCreado = computed(
    () => this.compras().find((c) => c.pedido.id === this.recienCreado())?.pedido.numero ?? '',
  );

  ngOnInit(): void {
    const nuevo = this.ruta.snapshot.queryParamMap.get('nuevo');
    if (nuevo) {
      this.recienCreado.set(+nuevo);
      this.expandido.set(+nuevo);
    }

    /*
     * Pedidos y envíos se piden a la vez y se cruzan aquí.
     *
     * El envío ya se creaba con el destino que el comprador eligió al pagar,
     * pero no se le enseñaba en ninguna pantalla: sabía qué había comprado y no
     * a dónde iba ni a nombre de quién, que es justo lo que se quiere comprobar
     * cuando se manda un pedido a otra persona.
     *
     * Si la lista de envíos falla, las compras se enseñan igual, sin la parte de
     * entrega: perder el detalle del reparto no puede dejar al comprador sin ver
     * lo que compró.
     */
    forkJoin({
      pedidos: this.pedidoService.mios(),
      envios: this.envioService.mios().pipe(catchError(() => of([] as Envio[]))),
    })
      .pipe(
        map(({ pedidos, envios }) => {
          const porPedido = new Map(envios.map((e) => [e.pedidoId, e]));
          return pedidos.map((pedido) => ({
            pedido,
            envio: porPedido.get(pedido.id) ?? null,
          }));
        }),
      )
      .subscribe({
        next: (compras) => {
          this.compras.set(compras);
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

  // Etiquetas, clases y reglas viven en los modelos: la vista solo las consulta.
  protected claseEstado(estado: EstadoPedido): string {
    return CLASE_ESTADO_PEDIDO[estado];
  }

  protected etiquetaEstado(estado: EstadoPedido): string {
    return ETIQUETA_ESTADO_PEDIDO[estado];
  }

  protected claseEstadoEnvio(estado: EstadoEnvio): string {
    return CLASE_ESTADO_ENVIO[estado];
  }

  protected etiquetaEstadoEnvio(estado: EstadoEnvio): string {
    return ETIQUETA_ESTADO_ENVIO[estado];
  }

  protected destino(envio: Envio): string {
    return destinoCompleto(envio);
  }

  protected pagaAlRecibir(pedido: Pedido): boolean {
    return pagaAlRecibir(pedido);
  }
}
