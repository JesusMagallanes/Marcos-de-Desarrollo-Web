import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { RUTAS_CATALOGO } from '../catalogo.routes';
import { Guia, GuiaRequest, GuiaResumen } from '../models';

/** Guías de ayuda — servicio `catalogo` (:8081). */
@Injectable({ providedIn: 'root' })
export class GuiaService {
  private readonly http = inject(HttpClient);

  /* La caché la pone `cacheInterceptor`; las escrituras de abajo la invalidan. */

  /**
   * GET /api/guias — público, solo las publicadas. Se cachea porque el pie las
   * pide en cada carga y cambian muy de vez en cuando.
   */
  listar(): Observable<GuiaResumen[]> {
    return this.http.get<GuiaResumen[]>(RUTAS_CATALOGO.guias.base);
  }

  /** GET /api/guias/{slug} — público; una guía sin publicar responde 404. */
  obtener(slug: string): Observable<Guia> {
    return this.http.get<Guia>(RUTAS_CATALOGO.guias.porSlug(slug));
  }

  /* ── Panel de administración ── */

  /*
   * Estas dos NO se cachean, y no es casualidad: traen los borradores, así que
   * enseñan cosas distintas de las que ve el público. La política las deja
   * fuera a propósito (ver el `(?!admin)` en `politica-cache.ts`).
   */

  /** GET /api/guias/admin/todas — incluye los borradores. */
  listarTodas(): Observable<GuiaResumen[]> {
    return this.http.get<GuiaResumen[]>(RUTAS_CATALOGO.guias.todas);
  }

  /** GET /api/guias/admin/{slug} — la guía completa, publicada o no. */
  obtenerParaEdicion(slug: string): Observable<Guia> {
    return this.http.get<Guia>(RUTAS_CATALOGO.guias.adminPorSlug(slug));
  }

  crear(dto: GuiaRequest): Observable<Guia> {
    return this.http.post<Guia>(RUTAS_CATALOGO.guias.base, dto);
  }

  actualizar(id: number, dto: GuiaRequest): Observable<Guia> {
    return this.http.put<Guia>(RUTAS_CATALOGO.guias.porId(id), dto);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(RUTAS_CATALOGO.guias.porId(id));
  }
}
