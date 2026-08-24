import { Injectable } from '@angular/core';
import { Observable, catchError, concat, defer, finalize, of, shareReplay, switchMap, tap } from 'rxjs';
import { EntradaCache, bd } from './db';

/**
 * TTL por defecto, en milisegundos. Elegidos por qué tan rápido cambian los
 * datos y cuánto importa que estén frescos: el catálogo es estable, las
 * valoraciones moderadas se mueven más.
 */
export const TTL = {
  /** Catálogo completo y fichas de producto. */
  productos: 10 * 60 * 1000,
  /** Listas de categorías y marcas: casi nunca cambian. */
  taxonomia: 60 * 60 * 1000,
  /** Valoraciones aprobadas de un producto. */
  valoraciones: 5 * 60 * 1000,
  /** Reseñas destacadas de portada. */
  destacadas: 10 * 60 * 1000,
} as const;

/**
 * Caché de lectura sobre IndexedDB, con estrategia stale-while-revalidate:
 *
 * <ol>
 *   <li>Dato fresco (dentro del TTL) → se sirve de local, cero red.</li>
 *   <li>Dato rancio → se sirve EN SEGUIDA la copia local y se refresca contra
 *       el backend en segundo plano; la interfaz nunca espera a la nube.</li>
 *   <li>Sin dato local → va a la red; si falla, propaga.</li>
 * </ol>
 *
 * <p>El caso 2 tiene una segunda red de seguridad: si el refresco falla
 * (offline, servicio caído), el suscriptor recibe igualmente la copia rancia
 * en lugar de un error. Mejor un precio de hace diez minutos que una pantalla
 * roja sin conexión.
 *
 * <h2>Invalidación sin carreras</h2>
 *
 * <p>Borrar de IndexedDB es asíncrono, y un "borrar y después leer" podía
 * leer ANTES de que el borrado terminara y servirse el dato recién
 * invalidado. Por eso cada entrada lleva la generación del dominio en que se
 * escribió, e {@link #invalidar} incrementa esa generación DE FORMA
 * SÍNCRONA: la entrada antigua queda huérfana al instante, aunque su borrado
 * físico tarde unos milisegundos más.
 */
@Injectable({ providedIn: 'root' })
export class CacheLecturaService {
  /** Peticiones idénticas en vuelo: una sola llamada aunque varios componentes pidan lo mismo. */
  private readonly enVuelo = new Map<string, Observable<unknown>>();

  /** Generación actual por dominio (`productos`, `valoraciones`, ...). */
  private readonly generaciones = new Map<string, number>();

  /**
   * Lee `clave` de la caché y, si no sirve, la carga con `cargar` y la guarda.
   *
   * @param clave clave estable que identifica el recurso (`productos:id:42`);
   *              el primer segmento es el dominio para las invalidaciones
   * @param ttlMs vigencia de la entrada
   * @param cargar fábrica LAZY de la petición: solo se invoca si hace falta red
   */
  obtener<T>(clave: string, ttlMs: number, cargar: () => Observable<T>): Observable<T> {
    // defer: la lectura de IndexedDB ocurre al SUSCRIBIRSE, no al llamar. Así
    // un componente puede construir el observable temprano sin disparar nada.
    return defer(async () => {
      const entrada = await bd.cache.get(clave);
      const vigente =
        entrada !== undefined &&
        entrada.expiraEn > Date.now() &&
        entrada.generacion === this.generacionDe(clave);

      if (vigente) {
        return of(entrada.valor as T);
      }

      const desdeRed = this.compartir(clave, cargar(), ttlMs);

      if (!entrada) {
        return desdeRed;
      }

      // Rancio pero existente: primero lo local (instantáneo) y detrás el
      // fresco cuando llegue. El componente pinta dos veces y gana.
      const conRespaldo = desdeRed.pipe(
        catchError(() =>
          // La red falló pero hay algo que enseñar: mejor eso que un error.
          of(entrada.valor as T),
        ),
      );
      return concat(of(entrada.valor as T), conRespaldo);
    }).pipe(switchMap((flujo) => flujo));
  }

  /** Borra las entradas cuyo plazo venció. Barato: usa el índice `expiraEn`. */
  async limpiarVencidas(): Promise<void> {
    await bd.cache.where('expiraEn').below(Date.now()).delete();
  }

  /**
   * Invalida por dominio (`productos`, `valoraciones`, ...).
   *
   * <p>Sincrónico en lo que importa: al volver, cualquier lectura posterior
   * ya considera vencidas las entradas del dominio, incluso si este borrado
   * físico todavía no terminó.
   */
  async invalidar(dominio: string): Promise<void> {
    this.enVuelo.forEach((_, clave) => {
      if (clave.startsWith(`${dominio}:`)) {
        this.enVuelo.delete(clave);
      }
    });
    this.generaciones.set(dominio, (this.generaciones.get(dominio) ?? 0) + 1);
    await bd.cache.where('clave').startsWith(`${dominio}:`).delete();
  }

  /** Vacía toda la caché. Para cerrar sesión o depurar. */
  async vaciar(): Promise<void> {
    this.enVuelo.clear();
    await bd.cache.clear();
  }

  private generacionDe(clave: string): number {
    const dominio = clave.slice(0, clave.indexOf(':'));
    return this.generaciones.get(dominio) ?? 0;
  }

  /**
   * Ejecuta la carga compartiéndola: suscripciones simultáneas a la misma
   * clave reutilizan UNA llamada HTTP, y el resultado queda en IndexedDB para
   * las siguientes sesiones.
   */
  private compartir<T>(clave: string, fuente: Observable<T>, ttlMs: number): Observable<T> {
    const existente = this.enVuelo.get(clave);
    if (existente) {
      return existente as Observable<T>;
    }

    const guardado = Date.now();
    const generacion = this.generacionDe(clave);
    const flujo = fuente.pipe(
      tap((valor) => {
        const entrada: EntradaCache = {
          clave,
          valor,
          guardadoEn: guardado,
          expiraEn: guardado + ttlMs,
          generacion,
        };
        // Fire-and-forget deliberado: la entrega no debe esperar al disco.
        void bd.cache.put(entrada);
      }),
      finalize(() => this.enVuelo.delete(clave)),
      shareReplay({ bufferSize: 1, refCount: false }),
    );

    this.enVuelo.set(clave, flujo);
    return flujo;
  }
}
