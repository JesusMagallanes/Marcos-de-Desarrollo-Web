import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { RUTAS_COMPRAS } from '../compras.routes';
import { CambioEstadoEnvio, Envio, EstadoEnvio } from '../models';

/** Envíos — servicio `compras` (:8083). */
@Injectable({ providedIn: 'root' })
export class EnvioService {
  private readonly http = inject(HttpClient);

  /** GET /api/envios — solo EMPLEADO o ADMINISTRADOR. */
  listar(estado?: EstadoEnvio): Observable<Envio[]> {
    let params = new HttpParams();
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http.get<Envio[]>(RUTAS_COMPRAS.envios.base, { params });
  }

  /** GET /api/envios/mios — los del usuario del token, resuelto del JWT. */
  mios(): Observable<Envio[]> {
    return this.http.get<Envio[]>(RUTAS_COMPRAS.envios.mios);
  }

  /** PATCH /api/envios/{id}/estado — EMPLEADO o ADMINISTRADOR. */
  cambiarEstado(id: number, estadoEnvio: EstadoEnvio): Observable<Envio> {
    const cuerpo: CambioEstadoEnvio = { estadoEnvio };
    return this.http.patch<Envio>(RUTAS_COMPRAS.envios.estado(id), cuerpo);
  }
}
