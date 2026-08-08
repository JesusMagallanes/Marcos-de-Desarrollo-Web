import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService, CarritoService, ErrorApi } from '../../core';

/** Aterrizaje del flujo de Google/Facebook. */
@Component({
  selector: 'app-oauth-callback',
  template: `
    <div class="contenedor">
      @if (error()) {
        <i class="fa-solid fa-circle-exclamation icono error"></i>
        <h5>No se pudo completar el inicio de sesión</h5>
        <p class="text-muted">{{ error() }}</p>
        <button type="button" class="btn btn-success" (click)="volverAlLogin()">Volver a intentar</button>
      } @else {
        <div class="spinner-border text-success" role="status"></div>
        <p class="text-muted mt-3">Completando tu inicio de sesión…</p>
      }
    </div>
  `,
  styles: `
    .contenedor {
      min-height: 60vh;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      text-align: center;
      padding: 2rem 1rem;
    }

    .icono {
      font-size: 3rem;
      margin-bottom: 1rem;
    }

    .icono.error {
      color: #d9534f;
    }
  `,
})
export class OauthCallback implements OnInit {
  private auth = inject(AuthService);
  private carrito = inject(CarritoService);
  private router = inject(Router);

  protected error = signal('');

  ngOnInit(): void {
    const fragmento = new URLSearchParams(window.location.hash.replace(/^#/, ''));
    const token = fragmento.get('token');
    const refresh = fragmento.get('refresh') ?? '';

    if (!token) {
      this.error.set('El proveedor no devolvió un token válido.');
      return;
    }

    this.auth.consumirTokensOAuth(token, refresh).subscribe({
      next: () => {
        // Se limpia el fragmento para que el token no quede en el historial.
        history.replaceState(null, '', '/oauth/callback');
        this.carrito.refrescar();
        this.router.navigateByUrl('/', { replaceUrl: true });
      },
      error: (e: ErrorApi) => this.error.set(e.mensaje),
    });
  }

  protected volverAlLogin(): void {
    this.router.navigate(['/login'], { replaceUrl: true });
  }
}
