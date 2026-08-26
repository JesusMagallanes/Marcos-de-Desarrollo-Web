import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { SIN_CACHE } from '../../shared/interceptors';
import { RUTAS_CATALOGO } from '../catalogo.routes';
import { Categoria, CategoriaRequest } from '../models';

/** Categorías — servicio `catalogo` (:8081). */
@Injectable({ providedIn: 'root' })
export class CategoriaService {
  private readonly http = inject(HttpClient);

  /*
   * La caché la pone `cacheInterceptor`, con su plazo, y cualquier escritura de
   * las de abajo la invalida sola.
   *
   * Aquí había un `shareReplay` guardado en un campo —el mismo que estaba
   * copiado en otros tres servicios— con dos problemas: no caducaba nunca, así
   * que una vez leída la lista se quedaba fija hasta recargar la página entera,
   * y había que acordarse de llamar a `invalidar()` en cada escritura nueva.
   */

  /** GET /api/categorias — público, ordenadas por nombre. Cacheado. */
  listar(): Observable<Categoria[]> {
    return this.http.get<Categoria[]>(RUTAS_CATALOGO.categorias.base);
  }

  /** Fuerza una lectura fresca, ignorando la caché. */
  recargar(): Observable<Categoria[]> {
    return this.http.get<Categoria[]>(RUTAS_CATALOGO.categorias.base, {
      context: new HttpContext().set(SIN_CACHE, true),
    });
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
    return this.http.post<Categoria>(RUTAS_CATALOGO.categorias.base, dto);
  }

  /** PUT /api/categorias/{id} — ADMINISTRADOR. */
  actualizar(id: number, dto: CategoriaRequest): Observable<Categoria> {
    return this.http.put<Categoria>(RUTAS_CATALOGO.categorias.porId(id), dto);
  }

  /** DELETE /api/categorias/{id} — ADMINISTRADOR. 409 si tiene productos. */
  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(RUTAS_CATALOGO.categorias.porId(id));
  }
}
