import { Component, computed, input, output, signal } from '@angular/core';

/** Un punto en el mapa. */
export interface Coordenadas {
  latitud: number;
  longitud: number;
}

/** Un trozo del mapa, ya colocado dentro de la cuadrícula. */
export interface Tesela {
  url: string;
  x: number;
  y: number;
}

/** Suficiente para reconocer la manzana sin llegar al portal. */
const ZOOM = 16;
const LADO = 256;

/** Cuadrícula de 3×3: cubre el ancho de la tarjeta y deja margen alrededor. */
const RADIO = 1;

/**
 * Las teselas del mapa alrededor de un punto, y dónde cae el punto dentro de
 * ellas.
 *
 * <p>Se calcula aquí y no en el componente para poder probarlo sin montar nada:
 * es la única parte con matemáticas, y equivocarse en un signo se ve como un
 * mapa de otro continente, no como un error.
 *
 * <p>La proyección es la de siempre en mapas web (Web Mercator): la longitud es
 * lineal y la latitud pasa por una tangente, que es lo que hace que Groenlandia
 * salga enorme.
 */
export function teselasAlrededor(c: Coordenadas): { teselas: Tesela[]; centro: { x: number; y: number } } {
  const lado = 2 ** ZOOM;
  const px = ((c.longitud + 180) / 360) * lado * LADO;
  const rad = (c.latitud * Math.PI) / 180;
  const py =
    ((1 - Math.log(Math.tan(rad) + 1 / Math.cos(rad)) / Math.PI) / 2) * lado * LADO;

  const tx = Math.floor(px / LADO);
  const ty = Math.floor(py / LADO);

  const teselas: Tesela[] = [];
  for (let j = -RADIO; j <= RADIO; j++) {
    for (let i = -RADIO; i <= RADIO; i++) {
      // Arriba y abajo del mundo no hay mapa: esas teselas no existen y pedirlas
      // daría un 404 y un hueco gris. A los lados sí se da la vuelta, porque el
      // meridiano 180 no es un borde real.
      const fila = ty + j;
      if (fila < 0 || fila >= lado) continue;
      const columna = ((tx + i) % lado + lado) % lado;

      teselas.push({
        url: `https://tile.openstreetmap.org/${ZOOM}/${columna}/${fila}.png`,
        x: (i + RADIO) * LADO,
        y: (j + RADIO) * LADO,
      });
    }
  }

  // Dónde cae el punto dentro de la cuadrícula. Es lo que luego se desplaza para
  // que quede justo en el centro de lo que se ve.
  return {
    teselas,
    centro: { x: RADIO * LADO + (px - tx * LADO), y: RADIO * LADO + (py - ty * LADO) },
  };
}

/**
 * Elegir un punto en el mapa y verlo.
 *
 * <p>La dirección escrita sigue siendo lo importante —es lo que lee el
 * repartidor— pero un texto no dice si el sitio es el correcto. Con el punto en
 * el mapa el usuario **comprueba** que es su casa antes de pagar, y quien
 * reparte sabe a dónde va aunque la referencia sea "la casa del portón verde".
 *
 * <h4>Por qué un mapa estático y no uno interactivo</h4>
 *
 * <p>La CSP del gateway declara `frame-src 'none'` y `script-src 'self'`: ni un
 * iframe de Google Maps ni su API de JavaScript pueden cargarse, y está bien que
 * sea así. Lo que sí permite es `img-src ... https:`.
 *
 * <p>De ahí que el mapa sea una imagen. Se ve el punto, que es lo que hacía
 * falta, sin abrir la CSP ni añadir una clave de API que mantener. Para mover el
 * marcador está el enlace a Google Maps, que abre en otra pestaña.
 */
@Component({
  selector: 'app-ubicacion',
  templateUrl: './ubicacion.html',
  styleUrl: './ubicacion.css',
})
export class Ubicacion {
  /** Texto de ayuda bajo el bloque. */
  readonly ayuda = input('Ayuda a que el reparto te encuentre.');

  readonly coordenadas = input<Coordenadas | null>(null);
  readonly cambio = output<Coordenadas | null>();

  /**
   * Solo enseñar el punto, sin poder cambiarlo.
   *
   * <p>Es el caso de quien revisa una solicitud: necesita ver el sitio para
   * decidir, pero no le corresponde moverlo. Mismo componente porque es el mismo
   * mapa; lo que cambia es quién manda sobre él.
   */
  readonly soloLectura = input(false);

  protected buscando = signal(false);
  protected aviso = signal('');
  protected enlacePegado = signal('');
  protected pegando = signal(false);

  protected tiene = computed(() => this.coordenadas() !== null);

  /**
   * El mapa, montado con las teselas de OpenStreetMap.
   *
   * <p>Se compone a mano en vez de pedirle la imagen ya hecha a un servicio de
   * mapas estáticos porque esos servicios se caen: el de OpenStreetMap
   * (`staticmap.openstreetmap.de`) dejó de existir y lo único que se veía era el
   * icono de imagen rota. Las teselas, en cambio, son la base de todo mapa web y
   * no van a desaparecer.
   *
   * <p>Sin clave de API a propósito: una clave hay que guardarla, rotarla y
   * vigilar que no se agote la cuota, y para enseñar un punto no compensa.
   */
  protected mapa = computed(() => {
    const c = this.coordenadas();
    return c ? teselasAlrededor(c) : null;
  });

  /**
   * Lo que centra el punto en el hueco visible.
   *
   * <p>La cuadrícula se ancla en el centro del contenedor y desde ahí se
   * desplaza justo lo que el punto está desviado dentro de ella.
   */
  protected desplazamiento = computed(() => {
    const m = this.mapa();
    return m ? `translate(${-m.centro.x}px, ${-m.centro.y}px)` : '';
  });

  /** Para abrirlo y moverlo si no cuadra. */
  protected urlGoogleMaps = computed(() => {
    const c = this.coordenadas();
    return c ? `https://www.google.com/maps?q=${c.latitud},${c.longitud}` : '';
  });

  protected texto = computed(() => {
    const c = this.coordenadas();
    return c ? `${c.latitud.toFixed(5)}, ${c.longitud.toFixed(5)}` : '';
  });

  /* ── Formas de poner el punto ── */

  /**
   * La del navegador. Se pide con un botón y no al cargar: un permiso de
   * ubicación que salta solo se deniega casi siempre, y una vez denegado no se
   * vuelve a preguntar.
   */
  protected usarMiUbicacion(): void {
    if (!navigator.geolocation) {
      this.aviso.set('Tu navegador no puede compartir la ubicación. Pega el enlace de Google Maps.');
      return;
    }

    this.buscando.set(true);
    this.aviso.set('');

    navigator.geolocation.getCurrentPosition(
      (pos) => {
        this.buscando.set(false);
        // Seis decimales son ~11 cm: más precisión no aporta nada y guarda más
        // datos de los necesarios sobre dónde vive alguien.
        this.emitir({
          latitud: Number(pos.coords.latitude.toFixed(6)),
          longitud: Number(pos.coords.longitude.toFixed(6)),
        });
      },
      () => {
        this.buscando.set(false);
        this.aviso.set('No pudimos obtener tu ubicación. Puedes pegar el enlace de Google Maps.');
      },
      { enableHighAccuracy: true, timeout: 8000, maximumAge: 300000 },
    );
  }

  /**
   * Pegar un enlace de Google Maps.
   *
   * <p>Es la vía que de verdad usa la gente: se busca su casa en Maps, comparte
   * y pega. Cubre además a quien deniega el permiso de ubicación o entra desde
   * un ordenador, donde la posición del navegador suele estar muy equivocada.
   */
  protected aplicarEnlace(): void {
    const c = extraerCoordenadas(this.enlacePegado());
    if (!c) {
      this.aviso.set('No encontramos las coordenadas en ese enlace. Copia el que trae @lat,lng.');
      return;
    }
    this.aviso.set('');
    this.enlacePegado.set('');
    this.pegando.set(false);
    this.emitir(c);
  }

  protected quitar(): void {
    this.aviso.set('');
    this.cambio.emit(null);
  }

  protected alternarPegado(): void {
    this.pegando.update((v) => !v);
    this.aviso.set('');
  }

  private emitir(c: Coordenadas): void {
    this.cambio.emit(c);
  }
}

/**
 * Saca la latitud y la longitud de lo que sea que pegue el usuario.
 *
 * <p>Google Maps reparte las coordenadas en varios formatos según de dónde se
 * copie el enlace, y no hay motivo para exigirle al usuario que acierte con
 * uno. Se aceptan los tres habituales y el par suelto:
 *
 * <ul>
 *   <li>{@code .../@-12.046374,-77.042793,17z} — de la barra de direcciones
 *   <li>{@code ...?q=-12.046374,-77.042793} — de "compartir"
 *   <li>{@code ...!3d-12.046374!4d-77.042793} — de algunos enlaces largos
 *   <li>{@code -12.046374, -77.042793} — pegado a mano
 * </ul>
 *
 * <p>Un enlace corto (maps.app.goo.gl) no lleva las coordenadas dentro, así que
 * no se puede resolver sin llamar a Google: para ese caso el mensaje pide el
 * enlace largo en vez de fallar sin explicar.
 */
export function extraerCoordenadas(texto: string): Coordenadas | null {
  const t = (texto ?? '').trim();
  if (!t) return null;

  /*
   * EL ORDEN IMPORTA. En un enlace largo de Google Maps conviven dos pares:
   * `@lat,lng` es donde esta centrado el mapa, y `!3d..!4d..` es el sitio en si.
   * Para una entrega manda el sitio: el centro puede estar desplazado si el
   * usuario movio el mapa antes de copiar.
   */
  const patrones = [
    /!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)/,
    /[?&]q=(-?\d+\.\d+),\s*(-?\d+\.\d+)/,
    /@(-?\d+\.\d+),(-?\d+\.\d+)/,
    /^(-?\d+\.\d+)\s*,\s*(-?\d+\.\d+)$/,
  ];

  for (const patron of patrones) {
    const m = t.match(patron);
    if (!m) continue;

    const latitud = Number(m[1]);
    const longitud = Number(m[2]);

    // Fuera de rango no es una coordenada: es otra cosa con la misma forma.
    if (latitud >= -90 && latitud <= 90 && longitud >= -180 && longitud <= 180) {
      return { latitud: Number(latitud.toFixed(6)), longitud: Number(longitud.toFixed(6)) };
    }
  }
  return null;
}
