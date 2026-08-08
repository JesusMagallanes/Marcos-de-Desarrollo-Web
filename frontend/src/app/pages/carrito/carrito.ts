import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CurrencyPipe } from '@angular/common';
import {
  CarritoService,
  ErrorApi,
  MetodoPago,
  MetodoPagoService,
  PagoService,
} from '../../core';

const UMBRAL_ENVIO_GRATIS = 200;
const COSTO_ENVIO = 15;

@Component({
  selector: 'app-carrito',
  imports: [RouterLink, CurrencyPipe],
  templateUrl: './carrito.html',
  styleUrl: './carrito.css',
})
export class Carrito implements OnInit {
  protected carrito = inject(CarritoService);
  private metodoPagoService = inject(MetodoPagoService);
  private pagoService = inject(PagoService);
  private router = inject(Router);
  private ruta = inject(ActivatedRoute);

  protected cargando = signal(true);
  protected procesando = signal(false);
  protected error = signal('');
  protected metodosPago = signal<MetodoPago[]>([]);
  protected metodoElegido = signal<number | null>(null);

  protected costoEnvio = computed(() =>
    this.carrito.subtotal() >= UMBRAL_ENVIO_GRATIS || this.carrito.subtotal() === 0 ? 0 : COSTO_ENVIO,
  );
  protected total = computed(() => this.carrito.subtotal() + this.costoEnvio());
  protected faltaParaEnvioGratis = computed(() =>
    Math.max(0, UMBRAL_ENVIO_GRATIS - this.carrito.subtotal()),
  );
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
      if (estado === 'approved' && paymentId) this.confirmar(paymentId);
      else if (estado === 'failure') this.error.set('El pago fue rechazado. Intenta con otro medio.');
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

  protected pagar(): void {
    const metodo = this.metodoElegido();
    if (!metodo || this.vacio()) return;

    this.procesando.set(true);
    this.error.set('');

    // El importe lo calcula el backend desde el carrito; aquí solo se elige el medio.
    this.pagoService.crearPreferencia(metodo).subscribe({
      next: (pref) => {
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
