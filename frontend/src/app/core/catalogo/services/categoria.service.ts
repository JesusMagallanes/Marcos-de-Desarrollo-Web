import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay, tap } from 'rxjs';
import { RUTAS_CATALOGO } from '../catalogo.routes';
import { Categoria, CategoriaRequest } from '../models';

/** Categorías — servicio `catalogo` (:8081). */
@Injectable({ providedIn: 'root' })
export class CategoriaService {
  private readonly http = inject(HttpClient);

  /** `refCount: false` mantiene el valor aunque no quede ningún suscriptor: */
  private cache$?: Observable<Categoria[]>;

  /** GET /api/categorias — público, ordenadas por nombre. Cacheado. */
  listar(): Observable<Categoria[]> {
    this.cache$ ??= this.http
      .get<Categoria[]>(RUTAS_CATALOGO.categorias.base)
      .pipe(shareReplay({ bufferSize: 1, refCount: false }));
    return this.cache$;
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
    this.cache$ = undefined;
  }
}
