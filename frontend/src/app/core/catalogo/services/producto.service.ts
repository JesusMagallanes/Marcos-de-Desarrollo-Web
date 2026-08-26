import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { SIN_CACHE } from '../../shared/interceptors';
import { PAGINACION } from '../../shared/config/constantes';
import { acotarTamanoPagina, normalizarBusqueda } from '../../shared/config/limites';
import { Pagina } from '../../shared/models';
import { RUTAS_CATALOGO } from '../catalogo.routes';
import {
  AplicarDescuentoRequest,
  EstadoDescuento,
  PaginaDescuentos,
  Portada,
  EstadoModeracion,
  Producto,
  ProductoRequest,
  QuitarDescuentoRequest,
  RechazoProducto,
} from '../models';

/** Productos — servicio `catalogo` (:8081). */
@Injectable({ providedIn: 'root' })
export class ProductoService {
  private readonly http = inject(HttpClient);

  /*
   * Aquí había una caché a mano —`cacheTodos$` con `shareReplay`— igual que en
   * otros tres servicios, y con dos límites: solo cubría la lista completa (la
   * búsqueda y el listado por categoría iban al servidor siempre) y no caducaba
   * nunca, así que una vez leída se quedaba fija hasta recargar la página.
   *
   * Ahora la pone `cacheInterceptor` para todas las lecturas del catálogo, con
   * su plazo, y cualquier escritura de las de abajo la invalida sola.
   */

  /**
   * GET /api/productos — paginado. `buscar` filtra por nombre o descripción.
   *
   * Devolvía el catálogo completo en un array. Ahora es una página, con el
   * mismo tope que el resto de listados: el servidor rechaza `size` por encima
   * de 100.
   */
  listar(
    buscar?: string | null,
    page = 0,
    size: number = PAGINACION.productosPorPagina,
  ): Observable<Pagina<Producto>> {
    return this.http.get<Pagina<Producto>>(RUTAS_CATALOGO.productos.base, {
      params: this.parametros(buscar, page, size),
    });
  }

  /**
   * Igual que `listar`, saltándose la caché.
   *
   * Lo usa el panel de descuentos: acaba de cambiar precios y necesita ver el
   * resultado, no lo que se leyó hace medio minuto.
   */
  recargar(
    buscar?: string | null,
    page = 0,
    size: number = PAGINACION.productosPorPagina,
  ): Observable<Pagina<Producto>> {
    return this.http.get<Pagina<Producto>>(RUTAS_CATALOGO.productos.base, {
      params: this.parametros(buscar, page, size),
      context: new HttpContext().set(SIN_CACHE, true),
    });
  }

  /**
   * GET /api/productos/portada — las tres listas de la portada, en un viaje.
   *
   * La portada pedía el catálogo entero y se quedaba con unas decenas de
   * productos. Esto las trae ya acotadas y agrupadas.
   */
  portada(): Observable<Portada> {
    return this.http.get<Portada>(RUTAS_CATALOGO.productos.portada);
  }

  /**
   * GET /api/productos/descuentos — el listado del panel, con sus conteos.
   *
   * Endpoint aparte del listado público porque lo que pide es distinto: filtra
   * por estado del descuento, admite categoría y marca, y devuelve además los
   * conteos por sección. El panel hacía los cuatro filtros y los cuatro conteos
   * en memoria, sobre el catálogo completo.
   *
   * Va siempre sin caché: es una pantalla de edición, y quien acaba de aplicar
   * un descuento tiene que ver el resultado, no lo de hace medio minuto.
   */
  paraDescuentos(filtros: FiltrosDescuento, page = 0, size = 25): Observable<PaginaDescuentos> {
    let params = new HttpParams()
      .set('estado', filtros.estado)
      .set('page', Math.max(0, page))
      .set('size', acotarTamanoPagina(size));

    if (filtros.categoriaId) params = params.set('categoriaId', filtros.categoriaId);
    if (filtros.marcaId) params = params.set('marcaId', filtros.marcaId);

    const termino = filtros.texto ? normalizarBusqueda(filtros.texto) : null;
    if (termino) params = params.set('search', termino);

    return this.http.get<PaginaDescuentos>(RUTAS_CATALOGO.productos.paraDescuentos, {
      params,
      context: new HttpContext().set(SIN_CACHE, true),
    });
  }

  private parametros(buscar: string | null | undefined, page: number, size: number): HttpParams {
    // El término se recorta al límite del backend: una búsqueda de 200
    // caracteres volvería con un 400 que se puede evitar sin salir del navegador.
    const termino = buscar ? normalizarBusqueda(buscar) : null;

    const params = new HttpParams()
      .set('page', Math.max(0, page))
      .set('size', acotarTamanoPagina(size));

    return termino ? params.set('search', termino) : params;
  }

  /** GET /api/productos/{id} — 404 si no existe. */
  obtener(id: number): Observable<Producto> {
    return this.http.get<Producto>(RUTAS_CATALOGO.productos.porId(id));
  }

  /** GET /api/productos/categoria/{slug} — paginado. Cacheado por página. */
  listarPorCategoria(
    slug: string,
    page = 0,
    size: number = PAGINACION.productosPorPagina,
  ): Observable<Pagina<Producto>> {
    // El backend rechaza size > 100; se acota aquí para no gastar una petición.
    const params = new HttpParams()
      .set('page', Math.max(0, page))
      .set('size', acotarTamanoPagina(size));

    return this.http.get<Pagina<Producto>>(RUTAS_CATALOGO.productos.porCategoria(slug), {
      params,
    });
  }

  /** POST /api/productos — ADMINISTRADOR. Devuelve 201. */
  crear(dto: ProductoRequest): Observable<Producto> {
    return this.http.post<Producto>(RUTAS_CATALOGO.productos.base, dto);
  }

  /** PUT /api/productos/{id} — ADMINISTRADOR. */
  actualizar(id: number, dto: ProductoRequest): Observable<Producto> {
    return this.http.put<Producto>(RUTAS_CATALOGO.productos.porId(id), dto);
  }

  /** DELETE /api/productos/{id} — ADMINISTRADOR. Devuelve 204. */
  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(RUTAS_CATALOGO.productos.porId(id));
  }

  /** POST /api/productos/descuento — aplica un descuento a un lote. ADMINISTRADOR. */
  aplicarDescuento(dto: AplicarDescuentoRequest): Observable<Producto[]> {
    return this.http.post<Producto[]>(RUTAS_CATALOGO.productos.descuento, dto);
  }

  /** POST /api/productos/descuento/limpiar — quita el descuento de un lote. ADMINISTRADOR. */
  quitarDescuento(dto: QuitarDescuentoRequest): Observable<Producto[]> {
    return this.http.post<Producto[]>(RUTAS_CATALOGO.productos.descuentoLimpiar, dto);
  }

  /* ── Productos de colaborador (SZ-B08) ── */

  /**
   * GET /productos/mios — todo lo suyo, aprobado o no.
   *
   * A diferencia del catálogo público, esta lista SÍ trae los pendientes y los
   * rechazados: el dueño necesita ver por qué le rechazaron algo para corregirlo.
   */
  mios(): Observable<Producto[]> {
    return this.http.get<Producto[]>(RUTAS_CATALOGO.productos.mios);
  }

  /** POST /productos/mios — nace PENDIENTE: nadie publica sin revisión. */
  crearMio(dto: ProductoRequest): Observable<Producto> {
    return this.http.post<Producto>(RUTAS_CATALOGO.productos.mios, dto);
  }

  /**
   * PUT /productos/mios/{id} — **vuelve a dejarlo PENDIENTE**.
   *
   * Conviene avisarlo en la interfaz: el colaborador espera que su cambio se vea
   * al momento, y el producto desaparece de la tienda hasta que lo revisen.
   */
  actualizarMio(id: number, dto: ProductoRequest): Observable<Producto> {
    return this.http.put<Producto>(RUTAS_CATALOGO.productos.mio(id), dto);
  }

  eliminarMio(id: number): Observable<void> {
    return this.http.delete<void>(RUTAS_CATALOGO.productos.mio(id));
  }

  /* ── Moderación (personal) ── */

  /** GET /productos/moderacion — sin estado devuelve los pendientes. */
  colaModeracion(estado?: EstadoModeracion): Observable<Producto[]> {
    const params = estado ? new HttpParams().set('estado', estado) : undefined;
    return this.http.get<Producto[]>(RUTAS_CATALOGO.productos.moderacion, { params });
  }

  aprobarProducto(id: number): Observable<Producto> {
    return this.http.post<Producto>(RUTAS_CATALOGO.productos.aprobarProducto(id), null);
  }

  /** El motivo es obligatorio: se le enseña al colaborador para que corrija. */
  rechazarProducto(id: number, dto: RechazoProducto): Observable<Producto> {
    return this.http.post<Producto>(RUTAS_CATALOGO.productos.rechazarProducto(id), dto);
  }
}

/** Lo que el panel de descuentos manda al servidor para acotar la lista. */
export interface FiltrosDescuento {
  estado: EstadoDescuento;
  categoriaId: number | null;
  marcaId: number | null;
  texto: string | null;
}
