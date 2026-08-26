import { HttpContextToken, HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { of, tap } from 'rxjs';
import { CacheHttp } from '../cache/cache-http';
import { politicaPara, recursoDe } from '../cache/politica-cache';

/**
 * Marca una petición para que NO se lea de la caché.
 *
 * <p>Es para «recargar»: el usuario pulsa un botón de actualizar y espera datos
 * frescos, no los de hace treinta segundos. La respuesta sí se guarda, así que
 * el resto de la pantalla se beneficia igual.
 *
 * <pre>
 *   http.get(url, { context: new HttpContext().set(SIN_CACHE, true) })
 * </pre>
 */
export const SIN_CACHE = new HttpContextToken<boolean>(() => false);

/**
 * Responde desde memoria lo que ya se ha pedido hace poco.
 *
 * <h4>Qué arregla</h4>
 *
 * <p>Cada navegación volvía a pedirlo todo. Entrar en la portada, abrir una
 * ficha y volver eran tres veces la misma lista de productos y otras tres la de
 * categorías. Había cachés a mano en cuatro servicios —el mismo `shareReplay`
 * copiado— pero solo cubrían la lista completa de cada recurso: el listado por
 * categoría, que es la pantalla más visitada de la tienda, no estaba cacheado
 * en ninguna parte. Y ninguna de las cuatro caducaba: una vez leídas, se
 * quedaban fijas hasta recargar la página entera.
 *
 * <h4>Las tres reglas</h4>
 *
 * <ol>
 *   <li><b>Solo GET</b>, y solo lo que aparece en la lista blanca de
 *       `POLITICAS`. Lo que no esté ahí viaja al servidor siempre.
 *   <li><b>Toda escritura invalida su recurso.</b> Un PUT sobre
 *       `/api/productos/7` tira la caché de `/api/productos` entera, ficha por
 *       ficha. Esto es lo que hace que la caché no necesite que nadie se
 *       acuerde de invalidarla a mano, que es como se rompían las de antes.
 *   <li><b>Solo se guardan las respuestas correctas.</b> Un error no se cachea
 *       nunca: si se guardara, un fallo de red de un segundo dejaría la pantalla
 *       rota durante todo el TTL, y recargar no lo arreglaría.
 * </ol>
 */
export const cacheInterceptor: HttpInterceptorFn = (req, next) => {
  const cache = inject(CacheHttp);

  // Una escritura cambia el recurso: lo guardado deja de valer al instante.
  if (req.method !== 'GET') {
    cache.invalidarRecurso(recursoDe(req.urlWithParams));
    return next(req);
  }

  const politica = politicaPara(req.urlWithParams);
  if (!politica) {
    return next(req);
  }

  // La clave lleva la query: `?page=2` y `?page=3` son respuestas distintas.
  const clave = req.urlWithParams;

  if (!req.context.get(SIN_CACHE)) {
    const guardada = cache.obtener(clave);
    if (guardada) {
      // `of` emite y completa sin tocar la red: esta es la petición que no se
      // hace, que es de lo que va todo esto.
      return of(guardada.clone());
    }
  }

  return next(req).pipe(
    tap((evento) => {
      // Solo el evento de respuesta completa; los de progreso no se guardan.
      if (evento instanceof HttpResponse && evento.ok) {
        cache.guardar(clave, evento, politica.ttlMs);
      }
    }),
  );
};
