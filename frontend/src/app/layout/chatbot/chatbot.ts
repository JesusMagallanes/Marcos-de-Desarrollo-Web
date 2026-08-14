import { Component, computed, effect, inject, signal, viewChild, type ElementRef } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ChatbotService, imagenDe, LIMITES, porcentajeDescuento, Producto } from '../../core';

interface Mensaje {
  autor: 'bot' | 'usuario';
  /** HTML que viene del backend. Angular lo sanea al pintarlo con [innerHTML]. */
  html: string;
  productos?: Producto[];
  /** Época en milisegundos, para la marca de tiempo. */
  fecha: number;
}

/** Clave del historial en localStorage (mismo prefijo que el resto de la app). */
const CLAVE_HISTORIAL = 'sz_chat_historial';

/** Cuántos mensajes se conservan al guardar, para no llenar el almacenamiento. */
const MAX_HISTORIAL = 50;

const MENSAJE_BIENVENIDA = {
  autor: 'bot',
  html: '¡Hola! 👋 Soy el asistente de <b>SmartZone</b>. Pregúntame por <b>ofertas</b>, <b>laptops</b>, <b>envíos</b> o <b>pagos</b>.',
  fecha: Date.now(),
} as const;

@Component({
  selector: 'app-chatbot',
  imports: [FormsModule],
  templateUrl: './chatbot.html',
})
export class Chatbot {
  private chat = inject(ChatbotService);
  private router = inject(Router);

  private cuerpo = viewChild.required<ElementRef<HTMLDivElement>>('cuerpo');

  protected abierto = signal(false);
  protected escribiendo = signal(false);
  protected entrada = signal('');
  protected mensajes = signal<Mensaje[]>(cargarHistorial());

  protected readonly imagenDe = imagenDe;
  protected readonly porcentajeDescuento = porcentajeDescuento;
  protected readonly maxLongitud = LIMITES.maxMensajeChat;

  protected readonly atajos = ['Ofertas', 'Laptops', 'Envíos', 'Métodos de pago', 'Contacto'];

  /** Los atajos solo se ofrecen antes de que el usuario escriba algo. */
  protected readonly mostrarAtajos = computed(
    () => !this.mensajes().some((m) => m.autor === 'usuario'),
  );

  constructor() {
    effect(() => {
      guardarHistorial(this.mensajes());
      if (this.abierto()) {
        // Un tick después: el mensaje nuevo ya está en el DOM cuando se lee la altura.
        setTimeout(() => this.irAlFinal(), 0);
      }
    });
  }

  protected alternar(): void {
    this.abierto.update((v) => !v);
    if (this.abierto()) {
      setTimeout(() => this.irAlFinal(), 0);
    }
  }

  /** Borra la conversación y vuelve al saludo inicial. */
  protected limpiar(): void {
    this.mensajes.set([{ ...MENSAJE_BIENVENIDA, fecha: Date.now() }]);
  }

  protected enviarAtajo(texto: string): void {
    this.entrada.set(texto);
    this.enviar();
  }

  protected enviar(): void {
    const texto = this.entrada().trim();
    if (!texto || this.escribiendo()) return;

    this.mensajes.update((m) => [
      ...m,
      { autor: 'usuario', html: this.escapar(texto), fecha: Date.now() },
    ]);
    this.entrada.set('');
    this.escribiendo.set(true);

    this.chat.enviar(texto).subscribe({
      next: (res) => {
        this.mensajes.update((m) => [
          ...m,
          { autor: 'bot', html: res.respuesta, productos: res.productos, fecha: Date.now() },
        ]);
        this.escribiendo.set(false);
      },
      error: () => {
        this.mensajes.update((m) => [
          ...m,
          {
            autor: 'bot',
            html: 'Ups, no pude responder ahora mismo. Intenta de nuevo.',
            fecha: Date.now(),
          },
        ]);
        this.escribiendo.set(false);
      },
    });
  }

  protected verProducto(id: number): void {
    this.abierto.set(false);
    this.router.navigate(['/producto', id]);
  }

  /** Hora corta (HH:MM) para la marca de tiempo de cada mensaje. */
  protected hora(fecha: number): string {
    return new Date(fecha).toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' });
  }

  /** El texto del usuario nunca se inyecta como markup. */
  private escapar(texto: string): string {
    return texto
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  private irAlFinal(): void {
    const cuerpo = this.cuerpo().nativeElement;
    cuerpo.scrollTop = cuerpo.scrollHeight;
  }
}

/** Lee el historial guardado; si falta o está roto, empieza de cero. */
function cargarHistorial(): Mensaje[] {
  try {
    const crudo = localStorage.getItem(CLAVE_HISTORIAL);
    if (!crudo) return [{ ...MENSAJE_BIENVENIDA, fecha: Date.now() }];
    const lista = JSON.parse(crudo) as Mensaje[];
    if (!Array.isArray(lista) || lista.length === 0) {
      return [{ ...MENSAJE_BIENVENIDA, fecha: Date.now() }];
    }
    return lista;
  } catch {
    return [{ ...MENSAJE_BIENVENIDA, fecha: Date.now() }];
  }
}

/** Guarda los mensajes recientes; un fallo de almacenamiento no rompe el chat. */
function guardarHistorial(mensajes: Mensaje[]): void {
  try {
    localStorage.setItem(CLAVE_HISTORIAL, JSON.stringify(mensajes.slice(-MAX_HISTORIAL)));
  } catch {
    /* almacenamiento lleno o no disponible */
  }
}
