import { Cargando } from '../../shared/cargando/cargando';
import { ElegirDireccion } from '../../shared/direccion/elegir-direccion';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CurrencyPipe } from '@angular/common';
import {
  AuthService,
  CarritoService,
  DireccionEntrega,
  ErrorApi,
  MetodoPago,
  MetodoPagoService,
  PagoService,
  direccionEnUnaLinea,
  esMercadoPago,
} from '../../core';

@Component({
  selector: 'app-carrito',
  imports: [RouterLink, CurrencyPipe, Cargando, ElegirDireccion],
  templateUrl: './carrito.html',
  styleUrl: './carrito.css',
})
export class Carrito implements OnInit {
  protected carrito = inject(CarritoService);
  private metodoPagoService = inject(MetodoPagoService);
  private pagoService = inject(PagoService);
  private auth = inject(AuthService);
  private router = inject(Router);
  private ruta = inject(ActivatedRoute);

  protected cargando = signal(true);
  protected procesando = signal(false);
  protected error = signal('');
  /** Aviso que no es un fallo: un pago aceptado pero todavía sin confirmar. */
  protected aviso = signal('');
  protected metodosPago = signal<MetodoPago[]>([]);
  protected metodoElegido = signal<number | null>(null);

  /*
   * No todos los medios llevan a una pasarela, y el botón lo decía igual:
   * ponía «Pagar con MercadoPago» aunque el comprador hubiera elegido pagar en
   * efectivo al recibir.
   */
  protected textoBotonPagar = computed(() => {
    const metodo = this.metodosPago().find((m) => m.id === this.metodoElegido());
    return metodo && !esMercadoPago(metodo) ? 'Confirmar pedido' : 'Pagar con MercadoPago';
  });

  /*
   * A dónde va el pedido. Se pide aquí, antes de ir a MercadoPago: si se pidiera
   * al volver, el comprador que cierra la pestaña dejaría un pedido pagado y sin
   * destino.
   *
   * Es una sola cosa y no nueve campos sueltos porque así viaja: la pasarela
   * quiere la dirección en partes para poder enseñarla y calcular el envío.
   */
  protected entrega = signal<DireccionEntrega | null>(null);
  protected direccionAbierta = signal(false);

  protected resumenDireccion = computed(() => {
    const e = this.entrega();
    return e ? direccionEnUnaLinea(e) : '';
  });

  /*
   * Envío y total vienen del backend, que es quien los cobra. Estaban aquí
   * calculados con una tercera copia del umbral y del costo —había otra en el
   * servicio de carrito y otra en `constantes`— y el backend no sumaba el envío
   * en absoluto: el comprador leía «Total S/ 215» y en la pasarela pagaba 200.
   */
  protected costoEnvio = this.carrito.costoEnvio;
  protected total = this.carrito.total;
  protected faltaParaEnvioGratis = this.carrito.faltaParaEnvioGratis;
  protected vacio = computed(() => this.carrito.items().length === 0);

  ngOnInit(): void {
    this.carrito.obtener().subscribe({
      next: () => this.cargando.set(false),
      error: (e: ErrorApi) => {
        this.error.set(e.mensaje);
        this.cargando.set(false);
      },
    });

    this.metodoPagoService.listar().subscribe({
      next: (m) => {
        this.metodosPago.set(m);
        if (m.length) this.metodoElegido.set(m[0].id);
      },
      // Sin métodos de pago el checkout es imposible: se avisa.
      error: (e: ErrorApi) => {
        this.metodosPago.set([]);
        this.error.set(e.mensaje);
      },
    });

    // Retorno desde MercadoPago: ?status=approved&payment_id=...
    this.ruta.queryParamMap.subscribe((q) => {
      const estado = q.get('status');
      const paymentId = q.get('payment_id');

      if (estado === 'approved' && paymentId) {
        this.confirmar(paymentId);
      } else if (estado === 'failure') {
        this.error.set('El pago fue rechazado. Intenta con otro medio.');
      } else if (estado === 'pending') {
        /*
         * MercadoPago tiene una tercera back_url y hasta ahora no la miraba
         * nadie: quien pagaba en efectivo, o con una tarjeta que quedó en
         * revisión, volvía a un carrito idéntico al que dejó y sin un solo
         * mensaje. Lo natural entonces es pagar otra vez.
         *
         * No se llama a `confirmar`: el pago aún no está hecho. La compra se
         * cierra sola cuando la pasarela avisa, o cuando el barrendero concilia.
         */
        this.aviso.set(
          'Tu pago quedó pendiente de confirmación. En cuanto la pasarela lo apruebe ' +
            'verás la compra en «Mis compras»; no hace falta que pagues otra vez.',
        );
      }
    });
  }

  protected cambiar(itemId: number, cantidad: number): void {
    if (cantidad < 1) return;
    this.carrito.cambiarCantidad(itemId, cantidad).subscribe({
      error: (e: ErrorApi) => this.error.set(e.mensaje),
    });
  }

  protected quitar(itemId: number): void {
    this.carrito.eliminar(itemId).subscribe({
      error: (e: ErrorApi) => this.error.set(e.mensaje),
    });
  }

  protected vaciar(): void {
    this.carrito.vaciar().subscribe({
      error: (e: ErrorApi) => this.error.set(e.mensaje),
    });
  }

  protected abrirDireccion(): void {
    this.direccionAbierta.set(true);
  }

  protected guardarDireccion(direccion: DireccionEntrega): void {
    this.entrega.set(direccion);
    this.direccionAbierta.set(false);
    this.error.set('');
  }

  protected pagar(): void {
    const metodo = this.metodoElegido();
    if (!metodo || this.vacio()) return;

    // El backend valida lo mismo. Esto está aquí para no mandar al comprador a
    // MercadoPago y traerlo de vuelta con un 400.
    const entrega = this.entrega();
    if (!entrega) {
      this.error.set('Añade la dirección de entrega antes de pagar.');
      this.abrirDireccion();
      return;
    }

    this.procesando.set(true);
    this.error.set('');
    this.aviso.set('');

    // El importe lo calcula el backend desde el carrito; aquí van el medio y el
    // destino. El destino se manda AHORA porque después el comprador se va a
    // MercadoPago y puede no volver: antes esto no se pedía y el pedido acababa
    // con la dirección literal "Por confirmar".
    this.pagoService
      .crearPreferencia(metodo, entrega)
      .subscribe({
        next: (pref) => {
          /*
           * Contra entrega: no hay a dónde ir. El backend ya reservó el stock,
           * creó el pedido y el envío, y lo dejó listo para cobrarse al
           * entregarlo, así que se lleva al comprador a verlo.
           */
          if (!pref.requierePasarela) {
            this.carrito.refrescar();
            this.router.navigate(['/perfil/compras'], {
              queryParams: pref.pedidoId ? { nuevo: pref.pedidoId } : {},
            });
            return;
          }

          const url = pref.init_point || pref.sandbox_init_point;
          if (url) {
            window.location.href = url;
          } else {
            this.procesando.set(false);
            this.error.set('El proveedor de pago no devolvió una URL de checkout.');
          }
        },
        error: (e: ErrorApi) => {
          this.procesando.set(false);
          this.error.set(e.mensaje);
        },
      });
  }

  private confirmar(paymentId: string): void {
    this.procesando.set(true);
    this.pagoService.confirmar(paymentId).subscribe({
      next: (pedido) => {
        this.procesando.set(false);
        this.carrito.refrescar();
        this.router.navigate(['/perfil/compras'], { queryParams: { nuevo: pedido.id } });
      },
      error: (e: ErrorApi) => {
        this.procesando.set(false);
        this.error.set(e.mensaje);
      },
    });
  }

}
