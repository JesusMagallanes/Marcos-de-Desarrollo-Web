import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { CacheLecturaService, TTL } from '../../offline';
import { RUTAS_CATALOGO } from '../catalogo.routes';
import { Categoria, CategoriaRequest } from '../models';

/** Categorías — servicio `catalogo` (:8081). */
@Injectable({ providedIn: 'root' })
export class CategoriaService {
  private readonly http = inject(HttpClient);
  private readonly cache = inject(CacheLecturaService);

  /**
   * GET /api/categorias — público, ordenadas por nombre.
   *
   * <p>La lista casi nunca cambia y TODAS las pantallas la piden (menú,
   * buscador, panel): es el caso perfecto para servir de IndexedDB y no
   * gastar una consulta a PostgreSQL en cada visita.
   */
  listar(): Observable<Categoria[]> {
    return this.cache.obtener('categorias:todas', TTL.taxonomia, () =>
      this.http.get<Categoria[]>(RUTAS_CATALOGO.categorias.base),
    );
  }

  /** Fuerza una lectura fresca, ignorando la caché. */
  recargar(): Observable<Categoria[]> {
    this.invalidar();
    return this.listar();
  }

  /** GET /api/categorias/{id} — público. */
  obtener(id: number): Observable<Categoria> {
    return this.http.get<Categoria>(RUTAS_CATALOGO.categorias.porId(id));
  }

  /** GET /api/categorias/slug/{slug} — es el que usa la tienda. */
  obtenerPorSlug(slug: string): Observable<Categoria> {
    return this.http.get<Categoria>(RUTAS_CATALOGO.categorias.porSlug(slug));
  }

  /** POST /api/categorias — ADMINISTRADOR. 409 si el nombre o el slug se repiten. */
  crear(dto: CategoriaRequest): Observable<Categoria> {
    return this.http
      .post<Categoria>(RUTAS_CATALOGO.categorias.base, dto)
      .pipe(tap(() => this.invalidar()));
  }

  /** PUT /api/categorias/{id} — ADMINISTRADOR. */
  actualizar(id: number, dto: CategoriaRequest): Observable<Categoria> {
    return this.http
      .put<Categoria>(RUTAS_CATALOGO.categorias.porId(id), dto)
      .pipe(tap(() => this.invalidar()));
  }

  /** DELETE /api/categorias/{id} — ADMINISTRADOR. 409 si tiene productos. */
  eliminar(id: number): Observable<void> {
    return this.http
      .delete<void>(RUTAS_CATALOGO.categorias.porId(id))
      .pipe(tap(() => this.invalidar()));
  }

  /** Descarta la caché: la siguiente lectura vuelve a ir al servidor. */
  invalidar(): void {
    void this.cache.invalidar('categorias');
  }
}
