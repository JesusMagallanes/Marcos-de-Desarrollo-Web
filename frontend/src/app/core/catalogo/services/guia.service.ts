import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay, tap } from 'rxjs';
import { RUTAS_CATALOGO } from '../catalogo.routes';
import { Guia, GuiaRequest, GuiaResumen } from '../models';

/** Guías de ayuda — servicio `catalogo` (:8081). */
@Injectable({ providedIn: 'root' })
export class GuiaService {
  private readonly http = inject(HttpClient);

  private cachePublicadas$?: Observable<GuiaResumen[]>;

  /**
   * GET /api/guias — público, solo las publicadas. Se cachea porque el pie las
   * pide en cada carga y cambian muy de vez en cuando.
   */
  listar(): Observable<GuiaResumen[]> {
    this.cachePublicadas$ ??= this.http
      .get<GuiaResumen[]>(RUTAS_CATALOGO.guias.base)
      .pipe(shareReplay({ bufferSize: 1, refCount: false }));
    return this.cachePublicadas$;
  }

  /** GET /api/guias/{slug} — público; una guía sin publicar responde 404. */
  obtener(slug: string): Observable<Guia> {
    return this.http.get<Guia>(RUTAS_CATALOGO.guias.porSlug(slug));
  }

  /* ── Panel de administración ── */

  /** GET /api/guias/admin/todas — incluye los borradores. */
  listarTodas(): Observable<GuiaResumen[]> {
    return this.http.get<GuiaResumen[]>(RUTAS_CATALOGO.guias.todas);
  }

  /** GET /api/guias/admin/{slug} — la guía completa, publicada o no. */
  obtenerParaEdicion(slug: string): Observable<Guia> {
    return this.http.get<Guia>(RUTAS_CATALOGO.guias.adminPorSlug(slug));
  }

  crear(dto: GuiaRequest): Observable<Guia> {
    return this.http.post<Guia>(RUTAS_CATALOGO.guias.base, dto).pipe(tap(() => this.invalidar()));
  }

  actualizar(id: number, dto: GuiaRequest): Observable<Guia> {
    return this.http
      .put<Guia>(RUTAS_CATALOGO.guias.porId(id), dto)
      .pipe(tap(() => this.invalidar()));
  }

  eliminar(id: number): Observable<void> {
    return this.http
      .delete<void>(RUTAS_CATALOGO.guias.porId(id))
      .pipe(tap(() => this.invalidar()));
  }

  /** Descarta la caché pública; se llama tras cualquier cambio del panel. */
  invalidar(): void {
    this.cachePublicadas$ = undefined;
  }
}
