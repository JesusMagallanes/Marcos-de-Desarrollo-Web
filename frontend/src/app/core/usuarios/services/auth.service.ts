import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, map, tap } from 'rxjs';
import { RUTAS_USUARIOS } from '../usuarios.routes';
import { ALMACENAMIENTO } from '../../shared/config/constantes';
import {
  AuthResponse,
  LoginRequest,
  PerfilUpdate,
  ProveedorDisponible,
  RefreshRequest,
  RegistroRequest,
  Usuario,
} from '../models';

/** Sesión y autenticación — servicio `usuarios` (:8082). */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly usuarioSig = signal<Usuario | null>(this.leerUsuarioGuardado());

  readonly usuario = this.usuarioSig.asReadonly();
  readonly autenticado = computed(() => this.usuarioSig() !== null);
  readonly rol = computed(() => this.usuarioSig()?.rol ?? null);
  readonly esAdmin = computed(() => this.rol() === 'ADMINISTRADOR');
  readonly esEmpleado = computed(() => this.rol() === 'EMPLEADO');
  readonly esStaff = computed(() => this.esAdmin() || this.esEmpleado());
  readonly esCuentaSocial = computed(() => {
    const proveedor = this.usuarioSig()?.proveedor;
    return proveedor !== undefined && proveedor !== 'LOCAL';
  });

  /* ── sesión ── */

  /** POST /api/auth/login — 401 si las credenciales no valen. */
  login(credenciales: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(RUTAS_USUARIOS.auth.login, credenciales)
      .pipe(tap((res) => this.guardarSesion(res)));
  }

  /** POST /api/auth/registrar — 201 al crear, 409 si el correo ya existe. */
  registrar(datos: RegistroRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(RUTAS_USUARIOS.auth.registrar, datos)
      .pipe(tap((res) => this.guardarSesion(res)));
  }

  /**
   * POST /api/auth/refresh El backend revoca el token usado y emite un par nuevo. Debe
   * llamarse una sola vez a la vez: un segundo canje del mismo token cuenta como reúso y
   * responde 401. De serializarlo se encarga el interceptor.
   */
  refrescar(): Observable<string> {
    const cuerpo: RefreshRequest = { refreshToken: this.refreshToken ?? '' };
    return this.http.post<AuthResponse>(RUTAS_USUARIOS.auth.refresh, cuerpo).pipe(
      tap((res) => this.guardarSesion(res)),
      map((res) => res.accessToken),
    );
  }

  /** GET /api/auth/yo — perfil del usuario del token. */
  yo(): Observable<Usuario> {
    return this.http.get<Usuario>(RUTAS_USUARIOS.auth.yo).pipe(tap((u) => this.setUsuario(u)));
  }

  /** POST /api/auth/logout — revoca el refresh token en el servidor. */
  logout(): void {
    const refresh = this.refreshToken;
    if (refresh) {
      // Si falla, igual se limpia en local: la sesión del navegador termina.
      this.http.post(RUTAS_USUARIOS.auth.logout, { refreshToken: refresh }).subscribe({
        error: () => void 0,
      });
    }
    this.limpiarSesion();
    this.router.navigate(['/']);
  }

  /* ── OAuth ── */

  /** GET /api/auth/proveedores — solo los que tienen credenciales configuradas. */
  proveedoresOAuth(): Observable<ProveedorDisponible[]> {
    return this.http.get<ProveedorDisponible[]>(RUTAS_USUARIOS.auth.proveedores);
  }

  /**
   * Cierra el flujo de Google/Facebook: el backend devolvió el navegador con los tokens
   * en el fragmento de la URL. Se guardan y se recupera el perfil.
   */
  consumirTokensOAuth(accessToken: string, refreshToken: string): Observable<Usuario> {
    localStorage.setItem(ALMACENAMIENTO.token, accessToken);
    if (refreshToken) {
      localStorage.setItem(ALMACENAMIENTO.refresh, refreshToken);
    }
    return this.yo();
  }

  /* ── perfil ── */

  /** PUT /api/usuarios/{id}/perfil — actualiza y refresca el usuario en memoria. */
  actualizarPerfil(id: number, datos: PerfilUpdate): Observable<Usuario> {
    return this.http
      .put<Usuario>(RUTAS_USUARIOS.usuarios.perfil(id), datos)
      .pipe(tap((u) => this.setUsuario(u)));
  }

  /* ── estado local ── */

  get token(): string | null {
    return localStorage.getItem(ALMACENAMIENTO.token);
  }

  get refreshToken(): string | null {
    return localStorage.getItem(ALMACENAMIENTO.refresh);
  }

  /** La llama el interceptor cuando el refresco falla. */
  limpiarSesion(): void {
    localStorage.removeItem(ALMACENAMIENTO.token);
    localStorage.removeItem(ALMACENAMIENTO.refresh);
    localStorage.removeItem(ALMACENAMIENTO.usuario);
    this.usuarioSig.set(null);
  }

  private guardarSesion(res: AuthResponse): void {
    localStorage.setItem(ALMACENAMIENTO.token, res.accessToken);
    if (res.refreshToken) {
      localStorage.setItem(ALMACENAMIENTO.refresh, res.refreshToken);
    }
    this.setUsuario(res.usuario);
  }

  private setUsuario(usuario: Usuario): void {
    localStorage.setItem(ALMACENAMIENTO.usuario, JSON.stringify(usuario));
    this.usuarioSig.set(usuario);
  }

  /** Rehidrata la sesión al recargar la página. */
  private leerUsuarioGuardado(): Usuario | null {
    const crudo = localStorage.getItem(ALMACENAMIENTO.usuario);
    if (!crudo) {
      return null;
    }
    try {
      return JSON.parse(crudo) as Usuario;
    } catch {
      localStorage.removeItem(ALMACENAMIENTO.usuario);
      return null;
    }
  }
}
