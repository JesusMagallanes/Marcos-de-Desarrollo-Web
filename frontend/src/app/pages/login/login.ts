import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  AuthService,
  CarritoService,
  ErrorApi,
  PATRON_PASSWORD,
  PATRON_TELEFONO,
  ProveedorDisponible,
} from '../../core';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login implements OnInit {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private carrito = inject(CarritoService);
  private router = inject(Router);
  private ruta = inject(ActivatedRoute);

  protected modo = signal<'login' | 'registro'>('login');
  protected enviando = signal(false);
  protected error = signal('');
  protected expirado = signal(false);

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
    this.ruta.queryParamMap.subscribe((q) => {
      this.expirado.set(q.get('expirado') === 'true');
      if (q.get('modo') === 'registro') this.modo.set('registro');

      // El backend redirige aquí con ?error=... si el flujo OAuth falla.
      const errorOauth = q.get('error');
      if (errorOauth) this.error.set(errorOauth);
    });
  }

  ngOnInit(): void {
    this.auth.proveedoresOAuth().subscribe({
      next: (p) => this.proveedores.set(p),
      // Sin proveedores configurados simplemente no se pintan los botones.
      error: () => this.proveedores.set([]),
    });
  }

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

  protected cambiarModo(modo: 'login' | 'registro'): void {
    this.modo.set(modo);
    this.error.set('');
  }

  protected entrar(): void {
    if (this.formLogin.invalid) {
      this.formLogin.markAllAsTouched();
      return;
    }
    this.enviando.set(true);
    this.error.set('');

    this.auth.login(this.formLogin.getRawValue()).subscribe({
      next: () => this.alEntrar(),
      // El interceptor ya normalizó el fallo: se lee ErrorApi, no HttpErrorResponse.
      error: (e: ErrorApi) => {
        this.enviando.set(false);
        this.error.set(e.noAutenticado ? 'Correo o contraseña incorrectos.' : e.mensaje);
      },
    });
  }

  protected registrarse(): void {
    if (this.formRegistro.invalid) {
      this.formRegistro.markAllAsTouched();
      return;
    }
    this.enviando.set(true);
    this.error.set('');

    this.auth.registrar(this.formRegistro.getRawValue()).subscribe({
      error: (e: ErrorApi) => {
        this.enviando.set(false);
        this.error.set(e.conflicto ? 'Ese correo ya está registrado.' : e.mensaje);
      },
      next: () => this.alEntrar(),
    });
  }

  private alEntrar(): void {
    this.enviando.set(false);
    this.carrito.refrescar();
    const destino = this.ruta.snapshot.queryParamMap.get('redirigir') ?? '/';
    this.router.navigateByUrl(destino);
  }

  protected campoInvalido(form: 'login' | 'registro', campo: string): boolean {
    // Los dos grupos tienen formas distintas, así que se resuelve cada uno por separado
    // en vez de unirlos (el `get` de la unión no es invocable).
    const control =
      form === 'login' ? this.formLogin.get(campo) : this.formRegistro.get(campo);
    return !!control && control.invalid && control.touched;
  }
}
