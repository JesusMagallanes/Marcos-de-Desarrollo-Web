import { Cargando } from '../../../shared/cargando/cargando';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import {
  Envio,
  EnvioService,
  ErrorApi,
  EstadoPedido,
  Pedido,
  PedidoService,
  LISTOS_PARA_ENVIAR,
  destinoCompleto,
  pagaAlRecibir,
  siguienteEstado,
  accionSiguiente,
} from '../../../core';

type Pestana = 'POR_ENVIAR' | 'EN_CAMINO' | 'ENTREGADOS';

/*
 * Qué estados de pedido caen en cada pestaña.
 *
 * Antes la pestaña ERA el estado, y ahí estaba el fallo: «Por enviar» filtraba
 * por PENDIENTE, que es un checkout abandonado sin pagar. Los pedidos PAGADOS
 * —los únicos que de verdad hay que preparar— no aparecían en ninguna pestaña,
 * y la lista de reparto se llenaba de compras que nadie llegó a pagar.
 */
const ESTADOS_DE: Record<Pestana, EstadoPedido[]> = {
  POR_ENVIAR: LISTOS_PARA_ENVIAR,
  EN_CAMINO: ['EN_TRANSITO'],
  ENTREGADOS: ['ENTREGADO'],
};

@Component({
  selector: 'app-admin-envios',
  imports: [CurrencyPipe, DatePipe, Cargando],
  templateUrl: './admin-envios.html',
  styleUrls: ['../admin-tabla.css', './admin-envios.css'],
})
export class AdminEnvios implements OnInit {
  private pedidoService = inject(PedidoService);
  private envioService = inject(EnvioService);

  protected cargando = signal(true);
  protected error = signal('');
  protected exito = signal('');
  protected todos = signal<Pedido[]>([]);
  protected pestana = signal<Pestana>('POR_ENVIAR');

  /*
   * El envío de cada pedido: destino, contacto y distancia desde la tienda.
   *
   * Se trae aparte y se cruza por `pedidoId` porque el dato vive en el envío, no
   * en el pedido: meterlo en la respuesta del pedido mezclaría dos cosas que hoy
   * están bien separadas. Es una consulta más, y esta pantalla la carga una vez.
   */
  protected envios = signal<Map<number, Envio>>(new Map());

  protected entregaDe(pedidoId: number): Envio | undefined {
    return this.envios().get(pedidoId);
  }

  protected readonly pestanas: { clave: Pestana; etiqueta: string; icono: string }[] = [
    { clave: 'POR_ENVIAR', etiqueta: 'Por enviar', icono: 'fa-box' },
    { clave: 'EN_CAMINO', etiqueta: 'En camino', icono: 'fa-truck' },
    { clave: 'ENTREGADOS', etiqueta: 'Entregados', icono: 'fa-circle-check' },
  ];

  protected visibles = computed(() =>
    this.todos().filter((p) => ESTADOS_DE[this.pestana()].includes(p.estado)),
  );

  protected contar(pestana: Pestana): number {
    return this.todos().filter((p) => ESTADOS_DE[pestana].includes(p.estado)).length;
  }

  ngOnInit(): void {
    this.cargar();
  }

  private cargar(): void {
    this.cargando.set(true);
    // Si esto falla no se rompe la pantalla: el destino es una ayuda, no el
    // contenido. Los pedidos se siguen viendo igual.
    this.envioService.listar().subscribe({
      next: (envios) => this.envios.set(new Map(envios.map((e) => [e.pedidoId, e]))),
      error: () => this.envios.set(new Map()),
    });

    this.pedidoService.listar().subscribe({
      next: (p) => {
        this.todos.set(p);
        this.cargando.set(false);
      },
      error: (e: ErrorApi) => {
        this.error.set(e.mensaje);
        this.cargando.set(false);
      },
    });
  }

  protected avanzar(pedido: Pedido): void {
    // La maquina de estados es la misma que valida el backend (pedido.model.ts),
    // asi no se ofrecen botones que acabarian en un 409.
    const siguiente = siguienteEstado(pedido.estado);
    if (!siguiente) return;

    this.pedidoService.cambiarEstado(pedido.id, siguiente).subscribe({
      next: () => {
        this.avisar(`Pedido ${pedido.numero} marcado como ${siguiente}.`);
        this.cargar();
      },
      error: (e: ErrorApi) => this.error.set(e.mensaje),
    });
  }

  protected cancelar(pedido: Pedido): void {
    this.pedidoService.cambiarEstado(pedido.id, 'CANCELADO').subscribe({
      next: () => {
        this.avisar(`Pedido ${pedido.numero} cancelado.`);
        this.cargar();
      },
      error: (e: ErrorApi) => this.error.set(e.mensaje),
    });
  }

  protected textoBoton(estado: EstadoPedido): string {
    return accionSiguiente(estado) ?? '';
  }

  protected destino(envio: Envio): string {
    return destinoCompleto(envio);
  }

  /** Contra entrega: hay que cobrar en la puerta, y eso se avisa aquí. */
  protected cobrarAlEntregar(pedido: Pedido): boolean {
    return pagaAlRecibir(pedido);
  }

  private avisar(texto: string): void {
    this.exito.set(texto);
    setTimeout(() => this.exito.set(''), 3000);
  }
}
