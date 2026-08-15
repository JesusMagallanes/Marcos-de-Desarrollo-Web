import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import {
  Envio,
  EnvioService,
  ErrorApi,
  EstadoPedido,
  Pedido,
  PedidoService,
  siguienteEstado,
  accionSiguiente,
} from '../../../core';

type Pestana = 'PENDIENTE' | 'EN_TRANSITO' | 'ENTREGADO';

@Component({
  selector: 'app-admin-envios',
  imports: [CurrencyPipe, DatePipe],
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
  protected pestana = signal<Pestana>('PENDIENTE');

  /*
   * Épica 3: a qué distancia queda cada entrega desde la tienda.
   *
   * Se trae aparte y se cruza por `pedidoId` porque el dato vive en el envío, no
   * en el pedido: meterlo en la respuesta del pedido mezclaría dos cosas que hoy
   * están bien separadas. Es una consulta más, y esta pantalla la carga una vez.
   */
  protected distancias = signal<Map<number, Envio>>(new Map());

  protected entregaDe(pedidoId: number): Envio | undefined {
    return this.distancias().get(pedidoId);
  }

  protected readonly pestanas: { clave: Pestana; etiqueta: string; icono: string }[] = [
    { clave: 'PENDIENTE', etiqueta: 'Por enviar', icono: 'fa-box' },
    { clave: 'EN_TRANSITO', etiqueta: 'En camino', icono: 'fa-truck' },
    { clave: 'ENTREGADO', etiqueta: 'Entregados', icono: 'fa-circle-check' },
  ];

  protected visibles = computed(() => this.todos().filter((p) => p.estado === this.pestana()));

  protected contar(estado: Pestana): number {
    return this.todos().filter((p) => p.estado === estado).length;
  }

  ngOnInit(): void {
    this.cargar();
  }

  private cargar(): void {
    this.cargando.set(true);
    // Si esto falla no se rompe la pantalla: la distancia es una ayuda, no el
    // contenido. Los pedidos se siguen viendo igual.
    this.envioService.listar().subscribe({
      next: (envios) => this.distancias.set(new Map(envios.map((e) => [e.pedidoId, e]))),
      error: () => this.distancias.set(new Map()),
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
        this.avisar(`Pedido #${pedido.id} marcado como ${siguiente}.`);
        this.cargar();
      },
      error: (e: ErrorApi) => this.error.set(e.mensaje),
    });
  }

  protected cancelar(pedido: Pedido): void {
    this.pedidoService.cambiarEstado(pedido.id, 'CANCELADO').subscribe({
      next: () => {
        this.avisar(`Pedido #${pedido.id} cancelado.`);
        this.cargar();
      },
      error: (e: ErrorApi) => this.error.set(e.mensaje),
    });
  }

  protected textoBoton(estado: EstadoPedido): string {
    return accionSiguiente(estado) ?? '';
  }

  private avisar(texto: string): void {
    this.exito.set(texto);
    setTimeout(() => this.exito.set(''), 3000);
  }
}
