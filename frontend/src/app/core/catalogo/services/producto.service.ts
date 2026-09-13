import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { CacheLecturaService, TTL } from '../../offline';
import { PAGINACION } from '../../shared/config/constantes';
import { acotarTamanoPagina, normalizarBusqueda } from '../../shared/config/limites';
import { Pagina } from '../../shared/models';
import { RUTAS_CATALOGO } from '../catalogo.routes';
import {
  AplicarDescuentoRequest,
  EstadoDescuento,
  FacetasCatalogo,
  FiltroCatalogo,
  PaginaDescuentos,
  Portada,
  EstadoModeracion,
  Producto,
  ProductoRequest,
  QuitarDescuentoRequest,
  RechazoProducto,
  filtroActivo,
  ordenBackend,
} from '../models';

/** Productos — servicio `catalogo` (:8081). */
@Injectable({ providedIn: 'root' })
export class ProductoService {
  private readonly http = inject(HttpClient);
  private readonly cache = inject(CacheLecturaService);

  /*
   * El catálogo lo cachea `CacheLecturaService`, en IndexedDB.
   *
   * Aquí hubo primero un `shareReplay` a mano y después el `cacheInterceptor`,
   * que guarda en memoria. Los dos se quedan cortos frente a este: IndexedDB
   * sobrevive a recargar la página y, sobre todo, sirve SIN CONEXIÓN, que es de
   * lo que va el modo offline. Cachear en las dos capas no aportaría nada y
   * daría dos caducidades distintas para el mismo dato, así que el catálogo
   * queda fuera de la lista blanca del interceptor.
   *
   * Lo que sigue siendo del interceptor es lo que esta capa no cubre: ubigeo,
   * métodos de pago y guías.
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
    filtro?: FiltroCatalogo | null,
  ): Observable<Pagina<Producto>> {
    const params = this.parametros(buscar, page, size, filtro);
    const pedir = () =>
      this.http.get<Pagina<Producto>>(RUTAS_CATALOGO.productos.base, { params });

    // Una búsqueda o un filtro no se cachean: los términos y las combinaciones
    // son infinitos y llenarían IndexedDB de entradas de un solo uso. Un orden
    // distinto del de por defecto tampoco, porque la página cacheada viene sin
    // ordenar. Las páginas «desnudas» sí, que son las que se repiten al ir y
    // volver.
    if (buscar || this.filtroFuerzaRed(filtro)) {
      return pedir();
    }
    return this.cache.obtener(
      `productos:pagina:${Math.max(0, page)}:${acotarTamanoPagina(size)}`,
      TTL.productos,
      pedir,
    );
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
    this.invalidar();
    return this.listar(buscar, page, size);
  }

  /**
   * GET /api/productos/portada — las tres listas de la portada, en un viaje.
   *
   * La portada pedía el catálogo entero y se quedaba con unas decenas de
   * productos. Esto las trae ya acotadas y agrupadas.
   */
  portada(): Observable<Portada> {
    // La primera pantalla que se abre y la que más se repite: es la que más
    // gana con estar disponible sin conexión.
    return this.cache.obtener('productos:portada', TTL.productos, () =>
      this.http.get<Portada>(RUTAS_CATALOGO.productos.portada),
    );
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

    // Sin caché a propósito: es una pantalla de edición, y quien acaba de
    // aplicar un descuento tiene que ver el resultado.
    return this.http.get<PaginaDescuentos>(RUTAS_CATALOGO.productos.paraDescuentos, { params });
  }

  private parametros(
    buscar: string | null | undefined,
    page: number,
    size: number,
    filtro?: FiltroCatalogo | null,
  ): HttpParams {
    // El término se recorta al límite del backend: una búsqueda de 200
    // caracteres volvería con un 400 que se puede evitar sin salir del navegador.
    const termino = buscar ? normalizarBusqueda(buscar) : null;

    let params = new HttpParams()
      .set('page', Math.max(0, page))
      .set('size', acotarTamanoPagina(size));

    if (termino) params = params.set('search', termino);
    return this.conFiltro(params, filtro);
  }

  /**
   * GET /api/productos/facetas — en qué se puede seguir filtrando.
   *
   * <p>Acepta los mismos filtros que el listado, así el rail cuenta sobre lo que
   * de verdad quedaría. No se cachea: cambia con cada combinación de filtros y
   * cachearla daría poco acierto y mucha entrada de un solo uso.
   */
  facetas(opciones: {
    buscar?: string | null;
    slug?: string | null;
    filtro?: FiltroCatalogo | null;
  }): Observable<FacetasCatalogo> {
    let params = new HttpParams();
    const termino = opciones.buscar ? normalizarBusqueda(opciones.buscar) : null;
    if (termino) params = params.set('search', termino);
    if (opciones.slug) params = params.set('slug', opciones.slug);
    params = this.conFiltro(params, opciones.filtro);
    return this.http.get<FacetasCatalogo>(RUTAS_CATALOGO.productos.facetas, { params });
  }

  /**
   * Añade a la petición los parámetros del filtro, con la convención del
   * backend: `marcaId` y `atributo` se repiten; el precio y la disponibilidad
   * solo viajan si aportan algo, para no ensuciar la URL ni saltarse la caché
   * sin motivo.
   */
  private conFiltro(params: HttpParams, filtro?: FiltroCatalogo | null): HttpParams {
    if (!filtro) return params;
    if (filtro.precioMin != null) params = params.set('precioMin', filtro.precioMin);
    if (filtro.precioMax != null) params = params.set('precioMax', filtro.precioMax);
    for (const id of filtro.marcaIds) params = params.append('marcaId', id);
    for (const atr of filtro.atributos) params = params.append('atributo', atr);
    if (filtro.soloDisponibles) params = params.set('soloDisponibles', true);
    if (filtro.orden && filtro.orden !== 'relevancia') {
      params = params.set('orden', ordenBackend(filtro.orden));
    }
    return params;
  }

  /** Si el filtro obliga a ir al servidor en vez de a la caché. */
  private filtroFuerzaRed(filtro?: FiltroCatalogo | null): boolean {
    return filtroActivo(filtro) || (!!filtro && filtro.orden !== 'relevancia');
  }

  /** GET /api/productos/{id} — 404 si no existe. */
  obtener(id: number): Observable<Producto> {
    return this.cache.obtener(`productos:id:${id}`, TTL.productos, () =>
      this.http.get<Producto>(RUTAS_CATALOGO.productos.porId(id)),
    );
  }

  /**
   * GET /api/productos/categoria/{slug} — paginado, ahora con filtros.
   *
   * <p>Sin filtros se comporta como antes y se cachea por página. Con filtros
   * (o con un orden distinto del de por defecto) va al servidor: es el backend
   * quien filtra, ordena y pagina sobre TODA la categoría, no el navegador
   * sobre los doce que le tocaron.
   */
  listarPorCategoria(
    slug: string,
    page = 0,
    size: number = PAGINACION.productosPorPagina,
    filtro?: FiltroCatalogo | null,
  ): Observable<Pagina<Producto>> {
    // El backend rechaza size > 100; se acota aquí para no gastar una petición.
    let params = new HttpParams()
      .set('page', Math.max(0, page))
      .set('size', acotarTamanoPagina(size));
    params = this.conFiltro(params, filtro);

    const pedir = () =>
      this.http.get<Pagina<Producto>>(RUTAS_CATALOGO.productos.porCategoria(slug), { params });

    if (this.filtroFuerzaRed(filtro)) {
      return pedir();
    }
    return this.cache.obtener(
      `productos:cat:${slug}:${Math.max(0, page)}:${acotarTamanoPagina(size)}`,
      TTL.productos,
      pedir,
    );
  }

  /** POST /api/productos — ADMINISTRADOR. Devuelve 201. */
  crear(dto: ProductoRequest): Observable<Producto> {
    return this.http
      .post<Producto>(RUTAS_CATALOGO.productos.base, dto)
      .pipe(tap(() => this.invalidar()));
  }

  /** PUT /api/productos/{id} — ADMINISTRADOR. */
  actualizar(id: number, dto: ProductoRequest): Observable<Producto> {
    return this.http
      .put<Producto>(RUTAS_CATALOGO.productos.porId(id), dto)
      .pipe(tap(() => this.invalidar()));
  }

  /** DELETE /api/productos/{id} — ADMINISTRADOR. Devuelve 204. */
  eliminar(id: number): Observable<void> {
    return this.http
      .delete<void>(RUTAS_CATALOGO.productos.porId(id))
      .pipe(tap(() => this.invalidar()));
  }

  /** POST /api/productos/descuento — aplica un descuento a un lote. ADMINISTRADOR. */
  aplicarDescuento(dto: AplicarDescuentoRequest): Observable<Producto[]> {
    return this.http
      .post<Producto[]>(RUTAS_CATALOGO.productos.descuento, dto)
      .pipe(tap(() => this.invalidar()));
  }

  /** POST /api/productos/descuento/limpiar — quita el descuento de un lote. ADMINISTRADOR. */
  quitarDescuento(dto: QuitarDescuentoRequest): Observable<Producto[]> {
    return this.http
      .post<Producto[]>(RUTAS_CATALOGO.productos.descuentoLimpiar, dto)
      .pipe(tap(() => this.invalidar()));
  }

  /**
   * Descarta la caché del catálogo: la siguiente lectura vuelve al servidor.
   *
   * <p>El prefijo se lleva por delante paginas, fichas, listados por categoría
   * y la portada de un golpe. Es lo que se quiere: un descuento cambia el
   * precio en todas ellas, y dejar una sola sin invalidar es justo la que
   * alguien acabaria mirando.
   */
  invalidar(): void {
    void this.cache.invalidar('productos');
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
