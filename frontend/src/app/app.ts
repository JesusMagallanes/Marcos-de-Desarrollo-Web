import { Component, computed, inject } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map } from 'rxjs';
import { Header } from './layout/header/header';
import { Footer } from './layout/footer/footer';
import { Chatbot } from './layout/chatbot/chatbot';
import { EstadoConexion } from './layout/estado-conexion/estado-conexion';
import { LoginModal } from './shared/login-modal/login-modal';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Header, Footer, Chatbot, EstadoConexion, LoginModal],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private router = inject(Router);

  private urlActual = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  /**
   * El panel admin trae su propio marco; el login ya es un modal que se abre
   * sobre la tienda, así que ahí sí va el header de tienda.
   */
  protected mostrarCascaron = computed(() => {
    const url = this.urlActual();
    return !url.startsWith('/admin');
  });

  protected mostrarChatbot = computed(() => this.mostrarCascaron());
}
