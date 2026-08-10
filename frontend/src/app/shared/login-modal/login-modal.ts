import {
  Component,
  ElementRef,
  OnInit,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import {
  AuthService,
  CarritoService,
  ErrorApi,
  PATRON_PASSWORD,
  PATRON_TELEFONO,
  ProveedorDisponible,
} from '../../core';

interface ModalBootstrap {
  show(): void;
  hide(): void;
}

/**
 * Modal global de acceso (login/registro), como el antiguo `Login-Register/Login.html`.
 * Lo abre el header sin navegar; las redirecciones de guards y del backend pasan por
 * la ruta /login, que termina llamando a {@link AuthService.abrirLogin}.
 */
@Component({
  selector: 'app-login-modal',
  imports: [ReactiveFormsModule],
  templateUrl: './login-modal.html',
  styleUrl: './login-modal.css',
})
export class LoginModal implements OnInit {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private carrito = inject(CarritoService);
  private router = inject(Router);

  private modalEl = viewChild<ElementRef<HTMLDivElement>>('modalAcceso');

  /** Evita que el cierre del modal tras autenticarse pise la navegación final. */
  private ocultandoTrasAutenticar = false;

  protected modo = computed(() => this.auth.modoLogin());
  protected error = computed(() => this.auth.errorLogin());
  protected expirado = computed(() => this.auth.expiradoLogin());
  protected enviando = signal(false);

  /** Solo se pintan los proveedores que el backend tiene configurados. */
  protected proveedores = signal<ProveedorDisponible[]>([]);

  protected formLogin = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  protected formRegistro = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2)]],
    lastname: ['', [Validators.required, Validators.minLength(2)]],
    emailAddress: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.pattern(PATRON_PASSWORD)]],
    phoneNumber: ['', [Validators.required, Validators.pattern(PATRON_TELEFONO)]],
    address: ['', [Validators.required]],
  });

  constructor() {
    effect(() => {
      if (this.auth.loginVisible()) {
        this.abrir();
      } else {
        this.instancia()?.hide();
      }
    });
  }

  ngOnInit(): void {
    this.auth.proveedoresOAuth().subscribe({
      next: (p) => this.proveedores.set(p),
      // Sin proveedores configurados simplemente no se pintan los botones.
      error: () => this.proveedores.set([]),
    });
  }

  ngAfterViewInit(): void {
    // Si el usuario cierra con la X, el fondo o Escape se sincroniza el estado;
    // en /login (redirección de un guard o del backend) se vuelve a la tienda.
    this.modalEl()?.nativeElement.addEventListener('hidden.bs.modal', () => {
      if (this.ocultandoTrasAutenticar) {
        this.ocultandoTrasAutenticar = false;
        this.auth.cerrarLogin();
        return;
      }
      if (this.auth.loginVisible()) {
        this.auth.cerrarLogin();
      }
      if (this.router.url.startsWith('/login')) {
        this.router.navigateByUrl('/');
      }
    });
  }

  /* ── apertura / cierre ── */

  private instancia(): ModalBootstrap | null {
    const el = this.modalEl()?.nativeElement;
    const bootstrap = (window as unknown as { bootstrap?: { Modal: unknown } }).bootstrap;
    const Modal = bootstrap?.Modal as
      | { getOrCreateInstance: (el: Element) => ModalBootstrap }
      | undefined;
    return el && Modal ? Modal.getOrCreateInstance(el) : null;
  }

  private abrir(): void {
    this.enviando.set(false);
    this.formLogin.reset({ email: '', password: '' });
    this.formRegistro.reset({
      name: '',
      lastname: '',
      emailAddress: '',
      password: '',
      phoneNumber: '',
      address: '',
    });
    this.instancia()?.show();
  }

  /** Cierra el modal (X/Cerrar). En /login, el evento `hidden` vuelve a la tienda. */
  protected cerrar(): void {
    this.instancia()?.hide();
  }

  private ocultar(): void {
    this.instancia()?.hide();
  }

  protected cambiarModo(modo: 'login' | 'registro'): void {
    this.auth.cambiarModoLogin(modo);
  }

  /* ── formularios ── */

  /**
   * Navegación de página completa, no fetch: el flujo de código de
   * autorización necesita que el navegador visite al proveedor.
   */
  protected entrarCon(proveedor: ProveedorDisponible): void {
    window.location.href = proveedor.url;
  }

  protected iconoDe(id: string): string {
    return id === 'google' ? '/Img/icon-google.png' : '/Img/icon-facebook2.png';
  }

  protected entrar(): void {
    if (this.formLogin.invalid) {
      this.formLogin.markAllAsTouched();
      return;
    }
    this.enviando.set(true);

    this.auth.login(this.formLogin.getRawValue()).subscribe({
      next: () => this.alEntrar(),
      // El interceptor ya normalizó el fallo: se lee ErrorApi, no HttpErrorResponse.
      error: (e: ErrorApi) => {
        this.enviando.set(false);
        this.auth.indicarErrorLogin(
          e.noAutenticado ? 'Correo o contraseña incorrectos.' : e.mensaje,
        );
      },
    });
  }

  protected registrarse(): void {
    if (this.formRegistro.invalid) {
      this.formRegistro.markAllAsTouched();
      return;
    }
    this.enviando.set(true);

    this.auth.registrar(this.formRegistro.getRawValue()).subscribe({
      error: (e: ErrorApi) => {
        this.enviando.set(false);
        this.auth.indicarErrorLogin(
          e.conflicto ? 'Ese correo ya está registrado.' : e.mensaje,
        );
      },
      next: () => this.alEntrar(),
    });
  }

  private alEntrar(): void {
    this.enviando.set(false);
    this.carrito.refrescar();
    this.ocultandoTrasAutenticar = true;
    this.ocultar();
    this.router.navigateByUrl(this.auth.urlTrasLogin());
  }

  protected campoInvalido(form: 'login' | 'registro', campo: string): boolean {
    // Los dos grupos tienen formas distintas, así que se resuelve cada uno por separado
    // en vez de unirlos (el `get` de la unión no es invocable).
    const control =
      form === 'login' ? this.formLogin.get(campo) : this.formRegistro.get(campo);
    return !!control && control.invalid && control.touched;
  }
}
