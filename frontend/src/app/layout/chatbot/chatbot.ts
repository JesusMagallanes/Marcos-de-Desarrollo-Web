import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ChatbotService, Producto } from '../../core';

interface Mensaje {
  autor: 'bot' | 'usuario';
  /** HTML que viene del backend. Angular lo sanea al pintarlo con [innerHTML]. */
  html: string;
  productos?: Producto[];
}

@Component({
  selector: 'app-chatbot',
  imports: [FormsModule],
  templateUrl: './chatbot.html',
})
export class Chatbot {
  private chat = inject(ChatbotService);
  private router = inject(Router);

  protected abierto = signal(false);
  protected escribiendo = signal(false);
  protected entrada = signal('');
  protected mensajes = signal<Mensaje[]>([
    {
      autor: 'bot',
      html: '¡Hola! 👋 Soy el asistente de <b>SmartZone</b>. Pregúntame por <b>ofertas</b>, <b>laptops</b>, <b>envíos</b> o <b>pagos</b>.',
    },
  ]);

  protected readonly atajos = ['Ofertas', 'Laptops', 'Envíos', 'Métodos de pago', 'Contacto'];

  protected alternar(): void {
    this.abierto.update((v) => !v);
  }

  protected enviarAtajo(texto: string): void {
    this.entrada.set(texto);
    this.enviar();
  }

  protected enviar(): void {
    const texto = this.entrada().trim();
    if (!texto || this.escribiendo()) return;

    this.mensajes.update((m) => [...m, { autor: 'usuario', html: this.escapar(texto) }]);
    this.entrada.set('');
    this.escribiendo.set(true);

    this.chat.enviar(texto).subscribe({
      next: (res) => {
        this.mensajes.update((m) => [
          ...m,
          { autor: 'bot', html: res.respuesta, productos: res.productos },
        ]);
        this.escribiendo.set(false);
      },
      error: () => {
        this.mensajes.update((m) => [
          ...m,
          { autor: 'bot', html: 'Ups, no pude responder ahora mismo. Intenta de nuevo.' },
        ]);
        this.escribiendo.set(false);
      },
    });
  }

  protected verProducto(id: number): void {
    this.abierto.set(false);
    this.router.navigate(['/producto', id]);
  }

  /** El texto del usuario nunca se inyecta como markup. */
  private escapar(texto: string): string {
    return texto
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }
}
