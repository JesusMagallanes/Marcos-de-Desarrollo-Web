import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { RUTAS_CATALOGO } from '../catalogo.routes';
import { Marca, MarcaRequest } from '../models';

/** Marcas — servicio `catalogo` (:8081). */
@Injectable({ providedIn: 'root' })
export class MarcaService {
  private readonly http = inject(HttpClient);

  /* La caché la pone `cacheInterceptor`; las escrituras de abajo la invalidan. */

  /** GET /api/marcas — público. Cacheado; las escrituras lo invalidan. */
  listar(): Observable<Marca[]> {
    return this.http.get<Marca[]>(RUTAS_CATALOGO.marcas.base);
  }

  /** GET /api/marcas/categoria/{id} — alimenta el select dependiente del admin. */
  listarPorCategoria(categoriaId: number): Observable<Marca[]> {
    return this.http.get<Marca[]>(RUTAS_CATALOGO.marcas.porCategoria(categoriaId));
  }

  /** POST /api/marcas — ADMINISTRADOR. 409 si el nombre se repite. */
  crear(dto: MarcaRequest): Observable<Marca> {
    return this.http.post<Marca>(RUTAS_CATALOGO.marcas.base, dto);
  }

  /** PUT /api/marcas/{id} — ADMINISTRADOR. */
  actualizar(id: number, dto: MarcaRequest): Observable<Marca> {
    return this.http.put<Marca>(RUTAS_CATALOGO.marcas.porId(id), dto);
  }

  /** DELETE /api/marcas/{id} — ADMINISTRADOR. 409 si tiene productos. */
  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(RUTAS_CATALOGO.marcas.porId(id));
  }
}
