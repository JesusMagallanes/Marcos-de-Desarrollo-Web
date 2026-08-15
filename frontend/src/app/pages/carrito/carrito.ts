import { Cargando } from '../../shared/cargando/cargando';
import { Component, OnInit, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CurrencyPipe } from '@angular/common';
import {
  AuthService,
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
  imports: [RouterLink, CurrencyPipe, Cargando],
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
  protected metodosPago = signal<MetodoPago[]>([]);
  protected metodoElegido = signal<number | null>(null);

  /*
   * Datos de entrega. Se piden aquí, antes de ir a MercadoPago: si se pidieran
   * al volver, el comprador que cierra la pestaña dejaría un pedido pagado y sin
   * destino.
   */
  protected direccionEnvio = signal('');
  protected referenciaEnvio = signal('');
  protected telefonoContacto = signal('');

  /*
   * Ubicación del punto de entrega (Épica 3). Sirve para que quien reparte vea
   * a qué distancia queda desde la tienda.
   *
   * Es OPCIONAL y se pide con un botón, no al cargar la página: un permiso de
   * ubicación que salta solo, sin que el usuario haya pedido nada, se deniega
   * casi siempre — y una vez denegado el navegador no vuelve a preguntar.
   */
  protected latitud = signal<number | undefined>(undefined);
  protected longitud = signal<number | undefined>(undefined);
  protected ubicandose = signal(false);
  protected avisoUbicacion = signal('');

  /*
   * Se prerrellena con lo del perfil: la mayoría manda el pedido a su propia
   * casa y reescribirlo cada vez sobra. Sigue siendo editable, porque a veces se
   * envía a otra persona.
   *
   * Es un `effect` y no una lectura en ngOnInit porque el usuario puede llegar
   * después (la sesión se restaura de forma asíncrona al cargar la app). Solo
   * escribe si el campo sigue vacío, para no pisar lo que ya esté tecleando.
   */
  private prerrelleno = effect(() => {
    const u = this.auth.usuario();
    if (!u) return;
    if (!this.direccionEnvio()) this.direccionEnvio.set(u.address ?? '');
    if (!this.telefonoContacto()) this.telefonoContacto.set(u.phoneNumber ?? '');
  });

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

  /**
   * Comprobación en el navegador, no la única: el backend valida lo mismo. Está
   * aquí para no mandar al usuario a MercadoPago y traerlo de vuelta con un 400.
   */
  protected entregaIncompleta(): boolean {
    return (
      this.direccionEnvio().trim().length < 5 ||
      !/^[0-9]{9}$/.test(this.telefonoContacto().trim())
    );
  }

  protected pagar(): void {
    const metodo = this.metodoElegido();
    if (!metodo || this.vacio()) return;

    if (this.entregaIncompleta()) {
      this.error.set('Completa la dirección de entrega y un teléfono de 9 dígitos.');
      return;
    }

    this.procesando.set(true);
    this.error.set('');

    // El importe lo calcula el backend desde el carrito; aquí van el medio y el
    // destino. El destino se manda AHORA porque después el comprador se va a
    // MercadoPago y puede no volver: antes esto no se pedía y el pedido acababa
    // con la dirección literal "Por confirmar".
    this.pagoService
      .crearPreferencia(metodo, {
        direccionEnvio: this.direccionEnvio(),
        referenciaEnvio: this.referenciaEnvio(),
        telefonoContacto: this.telefonoContacto(),
        latitud: this.latitud(),
        longitud: this.longitud(),
      })
      .subscribe({
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

  /**
   * Pide la ubicación al navegador.
   *
   * <p>Nunca bloquea la compra: si el usuario la deniega o el dispositivo no
   * puede, se avisa y se sigue igual. Lo único que se pierde es el cálculo de
   * distancia para quien reparte.
   */
  protected usarMiUbicacion(): void {
    if (!navigator.geolocation) {
      this.avisoUbicacion.set('Tu navegador no puede compartir la ubicación.');
      return;
    }

    this.ubicandose.set(true);
    this.avisoUbicacion.set('');

    navigator.geolocation.getCurrentPosition(
      (pos) => {
        // Seis decimales son ~11 cm: más precisión no aporta nada y solo
        // guarda datos de más sobre dónde vive alguien.
        this.latitud.set(Number(pos.coords.latitude.toFixed(6)));
        this.longitud.set(Number(pos.coords.longitude.toFixed(6)));
        this.ubicandose.set(false);
        this.avisoUbicacion.set('Ubicación añadida. Ayudará a que el reparto llegue antes.');
      },
      () => {
        this.ubicandose.set(false);
        // Ni error rojo ni insistir: es opcional de verdad.
        this.avisoUbicacion.set('Sin ubicación. Puedes comprar igual.');
      },
      { enableHighAccuracy: false, timeout: 8000, maximumAge: 300000 },
    );
  }

  protected tieneUbicacion(): boolean {
    return this.latitud() !== undefined && this.longitud() !== undefined;
  }
}
