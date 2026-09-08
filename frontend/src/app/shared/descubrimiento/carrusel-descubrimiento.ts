import { Component, computed, inject, input, signal } from '@angular/core';
import { Carrusel, DescubrimientoService, Producto } from '../../core';
import { ImpresionDirective } from './impresion.directive';
import { ProductoCard } from '../producto-card/producto-card';

/**
 * Un carrusel de recomendaciones, sea cual sea su procedencia.
 *
 * <p>Uno solo para los siete módulos. Comparten estructura —título, motivo,
 * fila de tarjetas— y hacer una implementación por módulo significaría arreglar
 * cuatro veces el mismo fallo de desplazamiento táctil.
 *
 * <p>Lo que cambia entre módulos es el TEXTO, y ese lo redacta el backend: el
 * título y el motivo llegan escritos. La interfaz no decide si algo es personal
 * o una tendencia general; se limita a no mezclarlos.
 */
@Component({
  selector: 'app-carrusel-descubrimiento',
  imports: [ProductoCard, ImpresionDirective],
  templateUrl: './carrusel-descubrimiento.html',
  styleUrl: './carrusel-descubrimiento.css',
})
export class CarruselDescubrimiento {
  readonly carrusel = input.required<Carrusel>();

  private readonly descubrimiento = inject(DescubrimientoService);

  /** Lo retirado en esta pantalla, para quitarlo sin recargar nada. */
  private readonly descartados = signal<Set<number>>(new Set());

  protected readonly visibles = computed<Producto[]>(() => {
    const fuera = this.descartados();
    return this.carrusel().items.filter((p) => !fuera.has(p.id));
  });

  /** Qué tarjeta tiene el menú abierto. Solo una a la vez. */
  protected readonly menuAbierto = signal<number | null>(null);

  protected alternarMenu(productoId: number): void {
    this.menuAbierto.update((abierto) => (abierto === productoId ? null : productoId));
  }

  /**
   * «No me interesa».
   *
   * <p>La tarjeta desaparece EN EL ACTO, sin esperar al servidor y sin recargar
   * el Home. Un carrusel que se reconstruye entero porque se descartó un
   * producto pierde la posición del desplazamiento y da la sensación de que
   * algo se rompió.
   *
   * <p>El hueco no se rellena a propósito: pedir un reemplazo sería otra
   * petición para tapar un sitio que el usuario acaba de vaciar a conciencia.
   * En la siguiente carga del Home ya no vuelve.
   */
  protected noMeInteresa(producto: Producto): void {
    this.menuAbierto.set(null);
    this.descartados.update((previos) => new Set(previos).add(producto.id));
    this.descubrimiento
      .noMeInteresa(producto.id, producto.categoriaId ?? undefined, this.carrusel().modulo)
      .subscribe();
  }

  /** Un clic en la tarjeta se atribuye al módulo del que salió. */
  protected alAbrir(producto: Producto): void {
    this.descubrimiento.vistaDeProducto(
      producto.id,
      producto.categoriaId ?? undefined,
      this.carrusel().modulo,
    );
  }
}
