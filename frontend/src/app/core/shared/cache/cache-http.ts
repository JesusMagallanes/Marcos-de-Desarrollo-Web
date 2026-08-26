import { HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';

interface Entrada {
  readonly respuesta: HttpResponse<unknown>;
  readonly expiraEn: number;
}

/**
 * Máximo de respuestas guardadas.
 *
 * <p>Existe porque las URLs con query son infinitas: cada búsqueda distinta y
 * cada página de un listado es una clave nueva. Sin tope, una sesión larga de
 * alguien navegando el catálogo va acumulando respuestas hasta que la pestaña
 * empieza a ir lenta, que es un fallo mucho más difícil de atribuir a la caché
 * que a cualquier otra cosa.
 */
const MAXIMO_ENTRADAS = 200;

/**
 * Las respuestas que ya tenemos, con su fecha de caducidad.
 *
 * <p>Vive en memoria y muere con la pestaña, a propósito: no se toca
 * `localStorage`. Una caché que sobrevive al cierre del navegador tiene que
 * responder a preguntas que aquí no hacen falta —qué pasa al desplegar una
 * versión nueva, qué pasa si entra otra persona en el mismo equipo— y el
 * problema que se está resolviendo es no repetir la misma petición tres veces
 * en la misma visita.
 *
 * <p>Sustituye a cuatro cachés escritas a mano —en `ProductoService`,
 * `CategoriaService`, `MarcaService` y `UbigeoService`— que eran el mismo
 * `shareReplay` copiado cuatro veces, ninguna con caducidad: una vez leída, la
 * lista se quedaba fija hasta recargar la página entera.
 */
@Injectable({ providedIn: 'root' })
export class CacheHttp {
  private readonly entradas = new Map<string, Entrada>();

  /** La respuesta guardada para esa URL, si sigue vigente. */
  obtener(clave: string): HttpResponse<unknown> | null {
    const entrada = this.entradas.get(clave);
    if (!entrada) {
      return null;
    }

    if (Date.now() >= entrada.expiraEn) {
      this.entradas.delete(clave);
      return null;
    }

    /*
     * Se vuelve a insertar para que quede como la más reciente.
     *
     * `Map` conserva el orden de inserción, así que reinsertarla al leerla
     * convierte el descarte de abajo en un LRU de verdad: lo que se sale es lo
     * que lleva más tiempo sin usarse, no lo que se pidió primero. Sin esto, el
     * catálogo —que se consulta en cada pantalla— sería lo primero en caer.
     */
    this.entradas.delete(clave);
    this.entradas.set(clave, entrada);
    return entrada.respuesta;
  }

  guardar(clave: string, respuesta: HttpResponse<unknown>, ttlMs: number): void {
    if (this.entradas.size >= MAXIMO_ENTRADAS && !this.entradas.has(clave)) {
      const masVieja = this.entradas.keys().next();
      if (!masVieja.done) {
        this.entradas.delete(masVieja.value);
      }
    }

    this.entradas.set(clave, { respuesta, expiraEn: Date.now() + ttlMs });
  }

  /**
   * Tira todo lo guardado de un recurso: `/api/productos` se lleva por delante
   * la lista, cada ficha y cada listado por categoría.
   *
   * @returns cuántas entradas se descartaron; se usa en las pruebas
   */
  invalidarRecurso(prefijo: string): number {
    let descartadas = 0;
    for (const clave of [...this.entradas.keys()]) {
      if (clave.startsWith(prefijo)) {
        this.entradas.delete(clave);
        descartadas++;
      }
    }
    return descartadas;
  }

  /**
   * Vacía la caché entera.
   *
   * <p>Se llama al entrar y al salir de la cuenta. Las respuestas guardadas son
   * todas públicas —la lista blanca de `POLITICAS` no admite otra cosa— así que
   * en teoría no haría falta; se hace igualmente porque «en teoría» es lo que
   * deja de ser cierto el día que alguien añade una política nueva sin
   * pararse a pensar si depende del usuario.
   */
  limpiar(): void {
    this.entradas.clear();
  }

  /** Cuántas respuestas hay guardadas ahora mismo. Para pruebas y diagnóstico. */
  get tamano(): number {
    return this.entradas.size;
  }
}
