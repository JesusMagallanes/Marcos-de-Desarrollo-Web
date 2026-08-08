import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { RUTAS_COMPRAS } from '../compras.routes';
import { CambioEstadoPedido, EstadoPedido, Pedido } from '../models';

/** Pedidos — servicio `compras` (:8083). */
@Injectable({ providedIn: 'root' })
export class PedidoService {
  private readonly http = inject(HttpClient);

  /** GET /api/pedidos/mios — los del usuario del token, más recientes primero. */
  mios(): Observable<Pedido[]> {
    return this.http.get<Pedido[]>(RUTAS_COMPRAS.pedidos.mios);
  }

  /** GET /api/pedidos — solo EMPLEADO o ADMINISTRADOR. */
  listar(estado?: EstadoPedido): Observable<Pedido[]> {
    let params = new HttpParams();
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http.get<Pedido[]>(RUTAS_COMPRAS.pedidos.base, { params });
  }

  /** GET /api/pedidos/{id} — el propio dueño o el staff. */
  obtener(id: number): Observable<Pedido> {
    return this.http.get<Pedido>(RUTAS_COMPRAS.pedidos.porId(id));
  }

  /** PATCH /api/pedidos/{id}/estado — EMPLEADO o ADMINISTRADOR. */
  cambiarEstado(id: number, estado: EstadoPedido): Observable<Pedido> {
    const cuerpo: CambioEstadoPedido = { estado };
    return this.http.patch<Pedido>(RUTAS_COMPRAS.pedidos.estado(id), cuerpo);
  }

  /** DELETE /api/pedidos/{id} — solo ADMINISTRADOR. */
  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(RUTAS_COMPRAS.pedidos.porId(id));
  }
}
