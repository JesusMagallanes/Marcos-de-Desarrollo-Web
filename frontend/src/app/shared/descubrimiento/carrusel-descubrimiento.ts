import {
  AfterViewInit,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { Carrusel, DescubrimientoService, Origen, Producto } from '../../core';
import { ImpresionDirective } from './impresion.directive';
import { ProductoCard } from '../producto-card/producto-card';

/** La etiqueta que encabeza cada carrusel, según de dónde salió. */
const ETIQUETAS: Record<Origen, { texto: string; icono: string }> = {
  TENDENCIA: { texto: 'Popular', icono: 'fa-fire' },
  PERSONAL: { texto: 'Para ti', icono: 'fa-wand-magic-sparkles' },
  GEO: { texto: 'En tu zona', icono: 'fa-location-dot' },
  EXPLORACION: { texto: 'Descubre', icono: 'fa-compass' },
  COHORTE: { texto: 'Otras personas', icono: 'fa-users' },
};

/**
 * Un carrusel de recomendaciones, sea cual sea su procedencia.
 *
 * <p>Uno solo para los siete módulos. Comparten estructura —título, motivo,
 * fila de tarjetas— y hacer una implementación por módulo significaría arreglar
 * cuatro veces el mismo fallo de desplazamiento táctil.
 *
 * <p>Lo que cambia entre módulos es el TEXTO, y ese lo redacta el backend: el
 * título y el motivo llegan escritos. La interfaz no decide si algo es personal
 * o una tendencia general; se limita a no mezclarlos. La etiqueta de origen
 * («Popular», «Para ti») es lo único que se pone aquí, y sale del `origen` que
 * ya viene en la respuesta.
 *
 * <p>Las tarjetas tienen SIEMPRE el mismo ancho, haya cuatro o doce: lo que no
 * cabe se desplaza. Antes la pista repartía el ancho entre todas las tarjetas,
 * así que con doce se aplastaban a un tercio y con cuatro se volvían enormes;
 * cada carrusel del Home tenía un tamaño distinto y ninguno el de las demás
 * secciones.
 */
@Component({
  selector: 'app-carrusel-descubrimiento',
  imports: [ProductoCard, ImpresionDirective],
  templateUrl: './carrusel-descubrimiento.html',
  styleUrl: './carrusel-descubrimiento.css',
})
export class CarruselDescubrimiento implements AfterViewInit {
  readonly carrusel = input.required<Carrusel>();

  private readonly descubrimiento = inject(DescubrimientoService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly pista = viewChild<ElementRef<HTMLElement>>('pista');

  /** Lo retirado en esta pantalla, para quitarlo sin recargar nada. */
  private readonly descartados = signal<Set<number>>(new Set());

  protected readonly visibles = computed<Producto[]>(() => {
    const fuera = this.descartados();
    return this.carrusel().items.filter((p) => !fuera.has(p.id));
  });

  protected readonly etiqueta = computed(() => ETIQUETAS[this.carrusel().origen]);

  /** Qué tarjeta tiene el menú abierto. Solo una a la vez. */
  protected readonly menuAbierto = signal<number | null>(null);

  /*
   * Hacia dónde queda pista por recorrer. Con ello las flechas se apagan en
   * los extremos y desaparecen del todo cuando todas las tarjetas caben, que
   * es lo que pasa en una pantalla ancha con pocas recomendaciones.
   */
  protected readonly hayAnterior = signal(false);
  protected readonly haySiguiente = signal(false);
  protected readonly desborda = computed(() => this.hayAnterior() || this.haySiguiente());

  ngAfterViewInit(): void {
    const el = this.pista()?.nativeElement;
    if (!el) {
      return;
    }
    this.medir();

    // Al cambiar el ancho —girar el móvil, abrir las herramientas— cambian los
    // extremos. Un `resize` global sería lo mismo con más ruido.
    if (typeof ResizeObserver !== 'undefined') {
      const observador = new ResizeObserver(() => this.medir());
      observador.observe(el);
      this.destroyRef.onDestroy(() => observador.disconnect());
    }
  }

  /** Una pantalla de tarjetas hacia un lado u otro. */
  protected desplazar(direccion: -1 | 1): void {
    const el = this.pista()?.nativeElement;
    if (!el) {
      return;
    }
    el.scrollBy({ left: direccion * el.clientWidth, behavior: 'smooth' });
  }

  protected medir(): void {
    const el = this.pista()?.nativeElement;
    if (!el) {
      return;
    }
    // Un margen de dos píxeles: el desplazamiento con `smooth` puede quedarse
    // a una fracción del final y dejar la flecha encendida sin nada que mover.
    const tope = el.scrollWidth - el.clientWidth;
    this.hayAnterior.set(el.scrollLeft > 2);
    this.haySiguiente.set(el.scrollLeft < tope - 2);
  }

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
    // Con una tarjeta menos puede que ya quepa todo y sobren las flechas.
    queueMicrotask(() => this.medir());
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
