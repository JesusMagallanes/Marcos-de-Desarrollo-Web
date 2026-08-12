import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay, tap } from 'rxjs';
import { PAGINACION } from '../../shared/config/constantes';
import { acotarTamanoPagina, normalizarBusqueda } from '../../shared/config/limites';
import { Pagina } from '../../shared/models';
import { RUTAS_CATALOGO } from '../catalogo.routes';
import {
  AplicarDescuentoRequest,
  Producto,
  ProductoRequest,
  QuitarDescuentoRequest,
} from '../models';

/** Productos — servicio `catalogo` (:8081). */
@Injectable({ providedIn: 'root' })
export class ProductoService {
  private readonly http = inject(HttpClient);

  private cacheTodos$?: Observable<Producto[]>;

  /** GET /api/productos — `buscar` filtra por nombre o descripción. */
  listar(buscar?: string): Observable<Producto[]> {
    // Se recorta al límite del backend: una búsqueda de 200 caracteres
    // volvería con un 400 que se puede evitar sin salir del navegador.
    const termino = buscar ? normalizarBusqueda(buscar) : null;

    if (termino) {
      const params = new HttpParams().set('search', termino);
      return this.http.get<Producto[]>(RUTAS_CATALOGO.productos.base, { params });
    }

    this.cacheTodos$ ??= this.http
      .get<Producto[]>(RUTAS_CATALOGO.productos.base)
      .pipe(shareReplay({ bufferSize: 1, refCount: false }));
    return this.cacheTodos$;
  }

  /** Fuerza una lectura fresca del catálogo completo. */
  recargar(): Observable<Producto[]> {
    this.invalidar();
    return this.listar();
  }

  /** GET /api/productos/{id} — 404 si no existe. */
  obtener(id: number): Observable<Producto> {
    return this.http.get<Producto>(RUTAS_CATALOGO.productos.porId(id));
  }

  /** GET /api/productos/categoria/{slug} — paginado, sin caché. */
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

  /** Descarta la caché. */
  invalidar(): void {
    this.cacheTodos$ = undefined;
  }
}
