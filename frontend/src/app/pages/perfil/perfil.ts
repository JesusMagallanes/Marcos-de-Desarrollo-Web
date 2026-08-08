import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService, iniciales as calcularIniciales } from '../../core';

@Component({
  selector: 'app-perfil',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './perfil.html',
  styleUrl: './perfil.css',
})
export class Perfil {
  protected auth = inject(AuthService);
  protected iniciales = signal('');

  constructor() {
    this.iniciales.set(calcularIniciales(this.auth.usuario()));
  }

  protected cerrarSesion(): void {
    this.auth.logout();
  }
}
