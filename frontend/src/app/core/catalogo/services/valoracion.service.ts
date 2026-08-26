import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { EMPTY, Observable, of, tap } from 'rxjs';
import { CacheLecturaService, ColaSyncService, ConexionService, TTL } from '../../offline';
import { RUTAS_CATALOGO } from '../catalogo.routes';
import {
  EstadoValoracion,
  Valoracion,
  ValoracionAdmin,
  ValoracionDestacada,
  ValoracionRequest,
} from '../models';

/** Valoraciones de productos — servicio `catalogo` (:8081). */
@Injectable({ providedIn: 'root' })
export class ValoracionService {
  private readonly http = inject(HttpClient);
  private readonly cache = inject(CacheLecturaService);
  private readonly conexion = inject(ConexionService);
  private readonly cola = inject(ColaSyncService);

  /**
   * GET /api/productos/{id}/valoraciones — público. Solo las aprobadas.
   *
   * <p>Caché offline-first: el detalle de producto es LA pantalla más visitada
   * y las reseñas aprobadas cambian poco. Rancia se muestra al instante y se
   * refresca detrás.
   */
  listar(productoId: number): Observable<Valoracion[]> {
    return this.cache.obtener(`valoraciones:prod:${productoId}`, TTL.valoraciones, () =>
      this.http.get<Valoracion[]>(RUTAS_CATALOGO.productos.valoraciones(productoId)),
    );
  }

  /** GET /api/productos/{id}/valoraciones/mia — la del usuario en curso, o null. */
  mia(productoId: number): Observable<Valoracion | null> {
    // Sin caché a propósito: depende de la sesión activa y una copia vieja
    // haría creer que ya valoraste cuando no es así (o al revés).
    return this.http.get<Valoracion | null>(RUTAS_CATALOGO.productos.valoracionesMia(productoId));
  }

  /**
   * POST /api/productos/{id}/valoraciones — crea o actualiza la del usuario.
   *
   * <p><b>Offline-first:</b> sin conexión la escritura queda en la cola de
   * IndexedDB y la interfaz recibe un eco optimista AL INSTANTE; al volver la
   * red, la cola confirma contra `/api/sync/valoraciones` con idempotencia.
   * Con conexión se comporta como siempre.
   */
  guardar(productoId: number, dto: ValoracionRequest): Observable<Valoracion> {
    if (!this.conexion.conectado()) {
      return this.encolarGuardado(productoId, dto);
    }

    return this.http
      .post<Valoracion>(RUTAS_CATALOGO.productos.valoraciones(productoId), dto)
      .pipe(tap(() => void this.cache.invalidar('valoraciones')));
  }

  /** DELETE /api/productos/{id}/valoraciones/mia — borra la del usuario en curso. */
  eliminar(productoId: number): Observable<void> {
    if (!this.conexion.conectado()) {
      void this.cola.encolar({
        tipo: 'VALORACION_ELIMINAR',
        claveEntidad: `valoracion:${productoId}`,
        url: RUTAS_CATALOGO.sync.valoraciones,
        cuerpoBase: { tipo: 'ELIMINAR', productoId },
      });
      // El borrado local ya lo aplicó el componente; nada que esperar.
      return EMPTY;
    }

    return this.http
      .delete<void>(RUTAS_CATALOGO.productos.valoracionesMia(productoId))
      .pipe(tap(() => void this.cache.invalidar('valoraciones')));
  }

  /**
   * GET /api/valoraciones/top — público. Las 6 aprobadas mejor valoradas,
   * cada una con el producto al que va el comentario.
   */
  destacadas(): Observable<ValoracionDestacada[]> {
    return this.cache.obtener('valoraciones:top', TTL.destacadas, () =>
      this.http.get<ValoracionDestacada[]>(RUTAS_CATALOGO.valoraciones.top),
    );
  }

  /* ── Moderación (ADMINISTRADOR) ── */

  /** GET /api/valoraciones/admin — todas, o solo las de un estado. */
  listarAdmin(estado?: EstadoValoracion): Observable<ValoracionAdmin[]> {
    const params = estado ? { estado } : undefined;
    return this.http.get<ValoracionAdmin[]>(RUTAS_CATALOGO.valoracionesAdmin.base, { params });
  }

  /** PATCH /api/valoraciones/admin/{id}/estado — aprobar, rechazar o reponer. */
  cambiarEstado(id: number, estado: EstadoValoracion): Observable<ValoracionAdmin> {
    return this.http
      .patch<ValoracionAdmin>(RUTAS_CATALOGO.valoracionesAdmin.cambiarEstado(id), {
        estado,
      })
      .pipe(tap(() => void this.cache.invalidar('valoraciones')));
  }

  /** DELETE /api/valoraciones/admin/{id} — retirar una reseña. */
  eliminarAdmin(id: number): Observable<void> {
    return this.http
      .delete<void>(RUTAS_CATALOGO.valoracionesAdmin.porId(id))
      .pipe(tap(() => void this.cache.invalidar('valoraciones')));
  }

  /* ── Escritura offline ── */

  /**
   * Encola el guardado y responde con un eco optimista.
   *
   * <p>El eco lleva id negativo: imposible confundirlo con una reseña real del
   * servidor. Cuando la cola confirme, la invalidación de la caché hace que la
   * siguiente lectura traiga la reseña verdadera; mientras tanto, el estado
   * PENDIENTE es honesto — así llegará también en el servidor.
   */
  private encolarGuardado(productoId: number, dto: ValoracionRequest): Observable<Valoracion> {
    void this.cola.encolar({
      tipo: 'VALORACION_GUARDAR',
      claveEntidad: `valoracion:${productoId}`,
      url: RUTAS_CATALOGO.sync.valoraciones,
      cuerpoBase: { tipo: 'GUARDAR', productoId, valoracion: dto },
    });

    const eco: Valoracion = {
      id: -(Date.now() % 1_000_000_000) - 1,
      nombre: dto.nombre,
      calificacion: dto.calificacion,
      comentario: dto.comentario,
      estado: 'PENDIENTE',
      creadoEn: new Date().toISOString(),
    };
    return of(eco);
  }
}
