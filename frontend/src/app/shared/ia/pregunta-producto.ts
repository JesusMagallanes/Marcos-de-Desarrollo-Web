import { Component, inject, input, signal } from '@angular/core';
import { ErrorApi, IaService, RespuestaFicha } from '../../core';

/**
 * «Pregúntale a este producto», en la ficha.
 *
 * Contesta con la ficha, las características y las reseñas aprobadas de ESE
 * producto. Si la ficha no lo dice, lo reconoce (`consta: false`) y se ofrece
 * el chat, en vez de que la IA supla el hueco con lo que cree saber del modelo.
 * Solo aparece si hay IA configurada.
 */
@Component({
  selector: 'app-pregunta-producto',
  templateUrl: './pregunta-producto.html',
  styleUrl: './ia-comun.css',
})
export class PreguntaProducto {
  readonly productoId = input.required<number>();

  protected readonly ia = inject(IaService);
  protected readonly pregunta = signal('');
  protected readonly preguntando = signal(false);
  protected readonly respuesta = signal<RespuestaFicha | null>(null);
  protected readonly error = signal('');

  protected readonly ejemplos = ['¿Sirve para editar video?', '¿Qué incluye la caja?', '¿Es buena para estudiar?'];

  protected usarEjemplo(texto: string): void {
    this.pregunta.set(texto);
    this.preguntar();
  }

  protected preguntar(): void {
    const texto = this.pregunta().trim();
    if (!texto || this.preguntando()) return;
    this.preguntando.set(true);
    this.error.set('');
    this.ia.preguntar(this.productoId(), texto).subscribe({
      next: (r) => {
        this.preguntando.set(false);
        if (r) {
          this.respuesta.set(r);
        } else {
          this.error.set('Ahora no puedo responder. Inténtalo en un momento.');
        }
      },
      error: (e: ErrorApi) => {
        this.preguntando.set(false);
        this.error.set(e.limitado ? 'Demasiadas preguntas seguidas. Espera un minuto.' : e.mensaje);
      },
    });
  }
}
