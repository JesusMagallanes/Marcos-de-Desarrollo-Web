import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService, SECCIONES_ADMIN } from '../../core';

@Component({
  selector: 'app-admin',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './admin.html',
  styleUrl: './admin.css',
})
export class Admin {
  private auth = inject(AuthService);

  /** Solo las secciones que el rol de la sesión puede gestionar. */
  protected readonly secciones = computed(() =>
    SECCIONES_ADMIN.filter((s) => this.auth.tienePermiso(s.permiso)),
  );

  /** En escritorio: sidebar minimizado a solo iconos. */
  protected colapsada = signal(false);

  /** En móvil: sidebar desplegada como cajón lateral. */
  protected abierta = signal(false);

  protected alternarMenu(): void {
    const enMovil = window.matchMedia('(max-width: 899.98px)').matches;
    if (enMovil) {
      this.abierta.update((v) => !v);
    } else {
      this.colapsada.update((v) => !v);
    }
  }

  protected cerrarMenu(): void {
    this.abierta.set(false);
  }
}
