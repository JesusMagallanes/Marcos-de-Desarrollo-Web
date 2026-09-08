import { Directive, ElementRef, OnDestroy, OnInit, inject, input } from '@angular/core';
import { DescubrimientoService } from '../../core';

/**
 * Registra una impresión cuando la tarjeta llega DE VERDAD a verse.
 *
 * <p>Que un carrusel contenga veinte productos no significa que se hayan
 * mostrado veinte: dieciséis están fuera del viewport, a la derecha, y puede
 * que nunca se desplacen hasta ahí. Contarlos todos inflaría las impresiones,
 * hundiría el CTR de cada módulo y —peor— dispararía la fatiga, que retiraría
 * de las recomendaciones productos que el usuario nunca llegó a ver.
 *
 * <p>Por eso `IntersectionObserver` y no el simple hecho de renderizar. Se pide
 * la mitad de la tarjeta visible: un píxel asomando por el borde no es haber
 * visto nada.
 *
 * <p>Se deja de observar en cuanto se registra. La impresión es un hecho que
 * ocurre una vez; seguir escuchando el desplazamiento del resto de la sesión
 * sería un coste permanente a cambio de nada.
 */
@Directive({
  selector: '[appImpresion]',
})
export class ImpresionDirective implements OnInit, OnDestroy {
  /** El carrusel donde aparece. Es la unidad de medida del recomendador. */
  readonly appImpresion = input.required<string>();
  readonly impresionItemId = input.required<number>();
  readonly impresionPosicion = input<number>();

  private readonly elemento = inject(ElementRef<HTMLElement>);
  private readonly descubrimiento = inject(DescubrimientoService);
  private observador: IntersectionObserver | null = null;

  ngOnInit(): void {
    // Navegador antiguo o entorno de pruebas sin la API: se registra al montar
    // en vez de quedarse sin ninguna señal. Perder precisión es mejor que
    // perder el dato entero.
    if (typeof IntersectionObserver === 'undefined') {
      this.registrar();
      return;
    }

    this.observador = new IntersectionObserver(
      (entradas) => {
        if (entradas.some((e) => e.isIntersecting)) {
          this.registrar();
          this.desconectar();
        }
      },
      { threshold: 0.5 },
    );
    this.observador.observe(this.elemento.nativeElement);
  }

  ngOnDestroy(): void {
    this.desconectar();
  }

  private registrar(): void {
    // El propio servicio ignora la repetición de `modulo:item`, así que un
    // re-render no genera una impresión nueva aunque la directiva se vuelva a
    // montar.
    this.descubrimiento.impresion(
      this.appImpresion(),
      this.impresionItemId(),
      this.impresionPosicion(),
    );
  }

  private desconectar(): void {
    this.observador?.disconnect();
    this.observador = null;
  }
}
