import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { EMPTY, Observable, catchError, of, tap } from 'rxjs';
import { RUTAS_DESCUBRIMIENTO } from '../descubrimiento.routes';
import {
  Carrusel,
  EventoRequest,
  HomeDescubrimiento,
  ImpresionRequest,
  IngestaResponse,
  TipoItem,
} from '../models';

/** Dónde vive el identificador del sujeto entre visitas. */
const CLAVE_SUJETO = 'sz.descubrimiento.sujeto';

/** Cada cuánto se vacía el buzón de eventos. */
const INTERVALO_ENVIO_MS = 5000;

/** Cuántos eventos se acumulan antes de mandarlos sin esperar al reloj. */
const TOPE_BUZON = 20;

/** Techo del tiempo en ficha; el backend aplica el suyo, este ahorra el viaje. */
const DWELL_MAXIMO_MS = 180_000;

/**
 * El único sitio desde el que se habla con Descubrimiento.
 *
 * <h4>El tracking nunca estorba</h4>
 *
 * <p>Todo lo que sale de aquí falla en silencio. Si el endpoint está caído, la
 * ficha del producto se abre igual, la búsqueda funciona igual y el Home se
 * pinta igual. Una plataforma que se rompe porque no pudo registrar una métrica
 * tiene las prioridades al revés.
 *
 * <h4>Se manda por lotes, no evento a evento</h4>
 *
 * <p>Explorar genera decenas de eventos por minuto. Una petición por evento
 * sería más tráfico que el propio catálogo y castigaría justo a quien navega
 * con datos móviles contados, que en Perú es la mayoría. Los eventos se
 * acumulan y salen cada cinco segundos, al llenarse el buzón, o cuando la
 * pestaña se oculta.
 *
 * <h4>El sujeto sobrevive al login</h4>
 *
 * <p>El identificador vive en `localStorage` y se manda en `X-Sujeto`. Al
 * iniciar sesión NO se borra: es lo que permite al backend fundir el rastro
 * anónimo con la cuenta. Ver `AuthService.limpiarSesion`.
 */
@Injectable({ providedIn: 'root' })
export class DescubrimientoService {
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);

  /** Identificador del sujeto; lo asigna el servidor y aquí solo se guarda. */
  private readonly sujetoSig = signal<string | null>(leerSujetoGuardado());
  readonly sujeto = this.sujetoSig.asReadonly();

  /** Una sesión por carga de la aplicación. Sirve para la saturación. */
  private readonly sesionId = crypto.randomUUID();

  /** El distrito, si se llega a saber. Sin él las tendencias van a nacional. */
  private ubigeoActual: string | null = null;

  private buzon: EventoRequest[] = [];
  private temporizador: ReturnType<typeof setTimeout> | null = null;

  /*
   * Memoria de lo ya emitido, para que un re-render no cuente como interés.
   *
   * Es el problema característico de un framework reactivo: el mismo componente
   * se puede recomponer varias veces por razones que nada tienen que ver con el
   * usuario, y sin esto cada recomposición sería una señal nueva.
   */
  private readonly vistasEmitidas = new Set<number>();
  private readonly impresionesEmitidas = new Set<string>();
  private ultimaBusqueda = '';

  constructor() {
    /*
     * La pestaña puede cerrarse con el buzón a medias. `visibilitychange` es el
     * único momento fiable para despedirse en móvil: `beforeunload` no se dispara
     * cuando el sistema mata la aplicación en segundo plano.
     */
    const alOcultarse = () => {
      if (document.visibilityState === 'hidden') {
        this.vaciarBuzon(true);
      }
    };
    document.addEventListener('visibilitychange', alOcultarse);
    this.destroyRef.onDestroy(() => {
      document.removeEventListener('visibilitychange', alOcultarse);
      this.vaciarBuzon(true);
    });
  }

  /* ══════════════ Contexto ══════════════ */

  /** Fija el distrito del sujeto. Se manda con cada lote. */
  fijarUbigeo(ubigeo: string | null): void {
    this.ubigeoActual = ubigeo && /^\d{6}$/.test(ubigeo) ? ubigeo : null;
  }

  /* ══════════════ Señales ══════════════ */

  /**
   * El usuario abrió la ficha de un producto.
   *
   * <p>Una sola vez por producto y por carga de la aplicación: volver atrás y
   * entrar de nuevo no es un interés nuevo, y el backend ya satura por sesión.
   */
  vistaDeProducto(productoId: number, categoriaId?: number, origen?: string): void {
    if (this.vistasEmitidas.has(productoId)) {
      return;
    }
    this.vistasEmitidas.add(productoId);
    this.encolar({
      tipo: 'ITEM_VIEW',
      itemTipo: 'PRODUCTO',
      itemId: productoId,
      categoriaId,
      origen,
    });
  }

  /**
   * Cuánto tiempo estuvo en la ficha.
   *
   * <p>Se manda UNA vez, al salir. Un latido cada segundo daría el mismo dato
   * con cien veces más peticiones.
   */
  salidaDeProducto(productoId: number, categoriaId: number | undefined, msEnPantalla: number): void {
    if (msEnPantalla < 1000) {
      return;
    }
    this.encolar({
      tipo: 'ITEM_VIEW_DEEP',
      itemTipo: 'PRODUCTO',
      itemId: productoId,
      categoriaId,
      dwellMs: Math.min(msEnPantalla, DWELL_MAXIMO_MS),
    });
  }

  vistaDeCategoria(categoriaId: number): void {
    this.encolar({ tipo: 'CATEGORY_VIEW', categoriaId });
  }

  /**
   * Una búsqueda ejecutada.
   *
   * <p>No una pulsación de tecla: esto se llama cuando la búsqueda ya se hizo.
   * El filtro contra el término anterior evita que recargar la página o volver
   * atrás cuente como una búsqueda más.
   */
  busqueda(termino: string): void {
    const limpio = termino.trim();
    if (!limpio || limpio === this.ultimaBusqueda) {
      return;
    }
    this.ultimaBusqueda = limpio;
    this.encolar({ tipo: 'SEARCH', termino: limpio });
  }

  /**
   * Un filtro por característica.
   *
   * <p>La señal más específica que se puede capturar sin preguntar: dice el
   * atributo Y el valor exactos. El código y el valor son los de
   * `producto_atributo`, tal como llegan en la ficha; aquí no se inventa
   * ninguna taxonomía paralela.
   */
  filtroAplicado(atributoCodigo: string, atributoValor: string, categoriaId?: number): void {
    this.encolar({ tipo: 'ATTRIBUTE_FILTER', atributoCodigo, atributoValor, categoriaId });
  }

  agregadoAlCarrito(productoId: number, categoriaId?: number): void {
    this.encolar({
      tipo: 'ADD_TO_CART',
      itemTipo: 'PRODUCTO',
      itemId: productoId,
      categoriaId,
    });
  }

  /**
   * «No me interesa».
   *
   * <p>Sale inmediatamente y no espera al lote: la interfaz va a retirar la
   * tarjeta en el acto, y si el usuario recarga antes del siguiente envío el
   * producto reaparecería. Es el único evento que justifica su propia petición.
   */
  noMeInteresa(productoId: number, categoriaId?: number, origen?: string): Observable<unknown> {
    return this.enviarLote([
      {
        tipo: 'NOT_INTERESTED',
        itemTipo: 'PRODUCTO',
        itemId: productoId,
        categoriaId,
        origen,
      },
    ]);
  }

  /**
   * Un producto que llegó a verse en pantalla.
   *
   * <p>Solo una vez por módulo y por carga: un carrusel se recompone al
   * desplazarlo y sin esta memoria el mismo producto generaría impresiones sin
   * parar, que además contarían como fatiga y lo harían desaparecer.
   */
  impresion(modulo: string, productoId: number, posicion?: number, itemTipo: TipoItem = 'PRODUCTO'): void {
    const clave = `${modulo}:${productoId}`;
    if (this.impresionesEmitidas.has(clave)) {
      return;
    }
    this.impresionesEmitidas.add(clave);
    this.enviarImpresiones([{ itemTipo, itemId: productoId, modulo, posicion }]);
  }

  /* ══════════════ Lectura ══════════════ */

  /**
   * El Home entero en UNA petición.
   *
   * <p>El backend ya devuelve los carruseles resueltos y de-duplicados entre
   * sí. Pedirlos por separado desde aquí traería productos repetidos entre
   * secciones, que es justo lo que la de-duplicación del servidor evita.
   */
  home(porCarrusel = 12): Observable<HomeDescubrimiento | null> {
    let params = new HttpParams().set('porCarrusel', porCarrusel);
    if (this.ubigeoActual) {
      params = params.set('ubigeo', this.ubigeoActual);
    }
    return this.http
      .get<HomeDescubrimiento>(RUTAS_DESCUBRIMIENTO.home, {
        params,
        headers: this.cabeceras(),
      })
      .pipe(
        tap((res) => this.guardarSujeto(res?.sujetoId)),
        // Si Descubrimiento falla, el Home se pinta con el catálogo de siempre.
        // El usuario no tiene por qué enterarse de que hay un recomendador.
        catchError(() => of(null)),
      );
  }

  /** Relacionados con un producto. No necesita sujeto: van por contenido. */
  similares(productoId: number, limite = 12): Observable<Carrusel | null> {
    return this.http
      .get<Carrusel>(RUTAS_DESCUBRIMIENTO.similares(productoId), {
        params: new HttpParams().set('limite', limite),
        headers: this.cabeceras(),
      })
      .pipe(catchError(() => of(null)));
  }

  /**
   * Suelta la identidad guardada y estrena una anónima.
   *
   * <p>Lo llama `AuthService` al cerrar sesión. Tras iniciar sesión el
   * identificador guardado ES el de la cuenta, y conservarlo dejaría al
   * siguiente visitante de este navegador alimentando —y viendo— el perfil de
   * quien se fue. No se pierde nada: lo aprendido quedó en la cuenta.
   *
   * <p>También se olvida lo emitido en esta pantalla, o el visitante nuevo
   * heredaría la memoria de vistas e impresiones del anterior.
   */
  olvidarSujetoLocal(): void {
    this.vaciarBuzon();
    this.sujetoSig.set(null);
    this.vistasEmitidas.clear();
    this.impresionesEmitidas.clear();
    this.ultimaBusqueda = '';
    try {
      localStorage.removeItem(CLAVE_SUJETO);
    } catch {
      // Sin almacenamiento no había nada que borrar.
    }
  }

  /* ══════════════ Fontanería ══════════════ */

  private encolar(evento: EventoRequest): void {
    this.buzon.push(evento);

    if (this.buzon.length >= TOPE_BUZON) {
      this.vaciarBuzon();
      return;
    }
    if (this.temporizador === null) {
      this.temporizador = setTimeout(() => this.vaciarBuzon(), INTERVALO_ENVIO_MS);
    }
  }

  /**
   * Manda lo acumulado.
   *
   * @param alCerrar si la pestaña se está ocultando; entonces se usa
   *   `sendBeacon`, que el navegador entrega aunque la página ya no exista.
   *   Una petición normal se cancelaría y se perderían los eventos de la última
   *   pantalla, que suele ser la más interesante.
   */
  private vaciarBuzon(alCerrar = false): void {
    if (this.temporizador !== null) {
      clearTimeout(this.temporizador);
      this.temporizador = null;
    }
    if (this.buzon.length === 0) {
      return;
    }
    const lote = this.buzon;
    this.buzon = [];

    if (alCerrar && navigator.sendBeacon) {
      const cuerpo = JSON.stringify({
        sesionId: this.sesionId,
        ubigeo: this.ubigeoActual ?? undefined,
        eventos: lote,
        // El sujeto no puede viajar en cabecera con sendBeacon, así que va en
        // la URL. El servidor lo lee igual que la cabecera.
      });
      const url = this.sujetoSig()
        ? `${RUTAS_DESCUBRIMIENTO.eventos}?sujeto=${this.sujetoSig()}`
        : RUTAS_DESCUBRIMIENTO.eventos;
      navigator.sendBeacon(url, new Blob([cuerpo], { type: 'application/json' }));
      return;
    }
    this.enviarLote(lote).subscribe();
  }

  private enviarLote(eventos: EventoRequest[]): Observable<unknown> {
    return this.http
      .post<IngestaResponse>(
        RUTAS_DESCUBRIMIENTO.eventos,
        { sesionId: this.sesionId, ubigeo: this.ubigeoActual ?? undefined, eventos },
        { headers: this.cabeceras() },
      )
      .pipe(
        tap((res) => this.guardarSujeto(res?.sujetoId)),
        catchError(() => EMPTY),
      );
  }

  private enviarImpresiones(impresiones: ImpresionRequest[]): void {
    this.http
      .post<IngestaResponse>(
        RUTAS_DESCUBRIMIENTO.impresiones,
        { impresiones },
        { headers: this.cabeceras() },
      )
      .pipe(
        tap((res) => this.guardarSujeto(res?.sujetoId)),
        catchError(() => EMPTY),
      )
      .subscribe();
  }

  private cabeceras(): HttpHeaders {
    const sujeto = this.sujetoSig();
    return sujeto ? new HttpHeaders({ 'X-Sujeto': sujeto }) : new HttpHeaders();
  }

  /**
   * Guarda el sujeto que devolvió el servidor.
   *
   * <p>El identificador SIEMPRE lo asigna el backend; aquí no se genera ninguno.
   * Al iniciar sesión el servidor devuelve el de la cuenta, y guardarlo es lo
   * que cierra la fusión del rastro anónimo.
   */
  private guardarSujeto(sujetoId: string | undefined): void {
    if (!sujetoId || sujetoId === this.sujetoSig()) {
      return;
    }
    this.sujetoSig.set(sujetoId);
    try {
      localStorage.setItem(CLAVE_SUJETO, sujetoId);
    } catch {
      // Modo privado o almacenamiento lleno: el sujeto vive solo en memoria y
      // se pierde al cerrar. Se degrada, no se rompe.
    }
  }
}

function leerSujetoGuardado(): string | null {
  try {
    return localStorage.getItem(CLAVE_SUJETO);
  } catch {
    return null;
  }
}
