import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { RUTAS_COMPRAS } from '../compras.routes';
import { MetodoPago, MetodoPagoRequest } from '../models';

/** Métodos de pago — servicio `compras` (:8083). */
@Injectable({ providedIn: 'root' })
export class MetodoPagoService {
  private readonly http = inject(HttpClient);

  /** GET /api/metodos-pago — requiere sesión. Lo usa el checkout. */
  listar(): Observable<MetodoPago[]> {
    return this.http.get<MetodoPago[]>(RUTAS_COMPRAS.metodosPago.base);
  }

  /** POST /api/metodos-pago — ADMINISTRADOR. 409 si el nombre se repite. */
  crear(dto: MetodoPagoRequest): Observable<MetodoPago> {
    return this.http.post<MetodoPago>(RUTAS_COMPRAS.metodosPago.base, dto);
  }

  /** PUT /api/metodos-pago/{id} — ADMINISTRADOR. */
  actualizar(id: number, dto: MetodoPagoRequest): Observable<MetodoPago> {
    return this.http.put<MetodoPago>(RUTAS_COMPRAS.metodosPago.porId(id), dto);
  }

  /** DELETE /api/metodos-pago/{id} — ADMINISTRADOR. 409 si tiene pedidos. */
  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(RUTAS_COMPRAS.metodosPago.porId(id));
  }
}
