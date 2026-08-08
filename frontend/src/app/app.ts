import { Component, computed, inject } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map } from 'rxjs';
import { Header } from './layout/header/header';
import { Footer } from './layout/footer/footer';
import { Chatbot } from './layout/chatbot/chatbot';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Header, Footer, Chatbot],
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

  /** El login y el panel admin traen su propio marco: ahí no va el header de tienda. */
  protected mostrarCascaron = computed(() => {
    const url = this.urlActual();
    return !url.startsWith('/login') && !url.startsWith('/admin');
  });

  protected mostrarChatbot = computed(() => this.mostrarCascaron());
}
