import { Directive, ElementRef, HostListener, inject, input } from '@angular/core';

/** Lo que se enseña cuando la imagen de verdad no está. */
export const IMAGEN_POR_DEFECTO = '/Img/img.png';

/**
 * Cuando una imagen no carga, pone el marcador de posición.
 *
 * <h4>Por qué hace falta</h4>
 *
 * <p>Las imágenes de los productos son URLs que escribe quien publica el
 * producto y que apuntan a otro sitio: no las alojamos nosotros. Cuando una de
 * esas URLs deja de responder —el que la alojaba la borró, cambió de dominio, o
 * simplemente bloqueó que se enlace desde fuera— el navegador pinta el icono de
 * imagen rota.
 *
 * <p>Las plantillas ya tenían un {@code src="imagen || '/Img/img.png'}, pero eso
 * solo cubre el caso de que NO haya URL. Si la hay y falla al cargarse, el
 * operador nunca llega a evaluarse: la URL existe, es la respuesta la que no
 * llega. Por eso hacía falta escuchar el error del propio elemento.
 *
 * <h4>El detalle que evita el bucle</h4>
 *
 * <p>Si el marcador de posición fallara también —porque se renombró, o porque
 * la caché guardó un 404— asignarlo dispararía otro {@code error}, que volvería
 * a asignarlo, indefinidamente. De ahí la marca de «ya lo intenté»: se sustituye
 * una sola vez por elemento.
 */
@Directive({
  /*
   * Sin el prefijo `app` a proposito, y por eso se silencia la regla.
   *
   * El prefijo existe para no chocar con directivas de terceros, pero aqui el
   * selector ES el punto: se aplica a toda imagen del componente que la
   * importa, sin tener que acordarse de anadir un atributo en cada `<img>`. Un
   * `img[appImagenCaida]` haria que la unica imagen sin proteger fuera,
   * justamente, la que a alguien se le olvidara marcar.
   */
  // eslint-disable-next-line @angular-eslint/directive-selector
  selector: 'img[src]',
})
export class ImagenCaida {
  private readonly elemento = inject<ElementRef<HTMLImageElement>>(ElementRef);

  /** Permite un marcador distinto donde el genérico quede raro (un avatar). */
  readonly reserva = input(IMAGEN_POR_DEFECTO);

  private yaSustituida = false;

  @HostListener('error')
  protected alFallar(): void {
    if (this.yaSustituida) {
      return;
    }
    this.yaSustituida = true;

    const img = this.elemento.nativeElement;
    if (img.getAttribute('src') !== this.reserva()) {
      img.setAttribute('src', this.reserva());
    }
  }
}
