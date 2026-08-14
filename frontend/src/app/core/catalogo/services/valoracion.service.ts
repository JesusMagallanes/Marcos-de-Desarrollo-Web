import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { RUTAS_CATALOGO } from '../catalogo.routes';
import {
  EstadoValoracion,
  Valoracion,
  ValoracionAdmin,
  ValoracionRequest,
} from '../models';

/** Valoraciones de productos — servicio `catalogo` (:8081). */
@Injectable({ providedIn: 'root' })
export class ValoracionService {
  private readonly http = inject(HttpClient);

  /** GET /api/productos/{id}/valoraciones — público. Solo las aprobadas. */
  listar(productoId: number): Observable<Valoracion[]> {
    return this.http.get<Valoracion[]>(RUTAS_CATALOGO.productos.valoraciones(productoId));
  }

  /** GET /api/productos/{id}/valoraciones/mia — la del usuario en curso, o null. */
  mia(productoId: number): Observable<Valoracion | null> {
    return this.http.get<Valoracion | null>(RUTAS_CATALOGO.productos.valoracionesMia(productoId));
  }

  /** POST /api/productos/{id}/valoraciones — crea o actualiza la del usuario. */
  guardar(productoId: number, dto: ValoracionRequest): Observable<Valoracion> {
    return this.http.post<Valoracion>(RUTAS_CATALOGO.productos.valoraciones(productoId), dto);
  }

  /** DELETE /api/productos/{id}/valoraciones/mia — borra la del usuario en curso. */
  eliminar(productoId: number): Observable<void> {
    return this.http.delete<void>(RUTAS_CATALOGO.productos.valoracionesMia(productoId));
  }

  /* ── Moderación (ADMINISTRADOR) ── */

  /** GET /api/valoraciones/admin — todas, o solo las de un estado. */
  listarAdmin(estado?: EstadoValoracion): Observable<ValoracionAdmin[]> {
    const params = estado ? { estado } : undefined;
    return this.http.get<ValoracionAdmin[]>(RUTAS_CATALOGO.valoracionesAdmin.base, { params });
  }

  /** PATCH /api/valoraciones/admin/{id}/estado — aprobar, rechazar o reponer. */
  cambiarEstado(id: number, estado: EstadoValoracion): Observable<ValoracionAdmin> {
    return this.http.patch<ValoracionAdmin>(RUTAS_CATALOGO.valoracionesAdmin.cambiarEstado(id), {
      estado,
    });
  }

  /** DELETE /api/valoraciones/admin/{id} — retirar una reseña. */
  eliminarAdmin(id: number): Observable<void> {
    return this.http.delete<void>(RUTAS_CATALOGO.valoracionesAdmin.porId(id));
  }
}
