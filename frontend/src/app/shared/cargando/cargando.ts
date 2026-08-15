import { Component, input } from '@angular/core';

/**
 * La misma animación que se ve al abrir la tienda, dentro de la aplicación.
 *
 * <p>Existe para que esperar signifique siempre lo mismo. Antes cada pantalla
 * ponía un `spinner-border` distinto, y cuando algo fallaba de verdad —una
 * sesión caducada, por ejemplo— la página escupía el mensaje del backend como
 * una línea suelta: "Necesitas iniciar sesión para esta operación". Eso es
 * correcto como respuesta HTTP y pésimo como pantalla.
 *
 * <p>El logo latiendo con su anillo ya lo asocia el usuario a "la tienda está
 * trabajando", porque es lo que ve cada vez que entra.
 */
@Component({
  selector: 'app-cargando',
  templateUrl: './cargando.html',
  styleUrl: './cargando.css',
})
export class Cargando {
  /** Qué se está esperando. Con un texto concreto la espera se hace más corta. */
  readonly texto = input('Cargando');

  /**
   * `bloque` ocupa el alto de su contenedor; `pantalla` ocupa la ventana.
   * Se usa `pantalla` cuando no hay nada más en la página que mirar.
   */
  readonly modo = input<'bloque' | 'pantalla'>('bloque');
}
