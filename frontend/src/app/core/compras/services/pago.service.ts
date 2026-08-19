import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { RUTAS_COMPRAS } from '../compras.routes';
import { ConfirmarPagoRequest, DireccionEntrega, Pedido, PreferenciaRequest, PreferenciaResponse } from '../models';

/** Checkout — servicio `compras` (:8083). Fachada de la saga de compra. */
@Injectable({ providedIn: 'root' })
export class PagoService {
  private readonly http = inject(HttpClient);

  /**
   * POST /api/pagos/preferencia
   *
   * El importe lo recalcula el servidor releyendo el carrito, así que no se
   * puede manipular desde el navegador. Lo que sí viaja es el destino, en
   * partes: MercadoPago lo necesita así para enseñarlo en su pantalla y para
   * calcular el envío por código postal.
   */
  crearPreferencia(metodoPagoId: number, entrega: DireccionEntrega): Observable<PreferenciaResponse> {
    const cuerpo: PreferenciaRequest = {
      metodoPagoId,
      entrega: {
        ...entrega,
        calle: entrega.calle.trim(),
        numero: entrega.numero.trim(),
        // Cadena vacía y `undefined` no son lo mismo para el backend: el campo
        // es opcional, así que si no hay referencia no se manda.
        referencia: entrega.referencia?.trim() || undefined,
        codigoPostal: entrega.codigoPostal.trim(),
        distrito: entrega.distrito.trim(),
        provincia: entrega.provincia.trim(),
        departamento: entrega.departamento.trim(),
        receptorNombre: entrega.receptorNombre.trim(),
        telefonoContacto: entrega.telefonoContacto.trim(),
      },
    };
    return this.http.post<PreferenciaResponse>(RUTAS_COMPRAS.pagos.preferencia, cuerpo);
  }

  /**
   * POST /api/pagos/confirmar Idempotente: reintentar con el mismo `paymentId` devuelve
   * el pedido ya creado en vez de duplicarlo, así que recargar la página de retorno es
   * seguro.
   */
  confirmar(paymentId: string): Observable<Pedido> {
    const cuerpo: ConfirmarPagoRequest = { paymentId };
    return this.http.post<Pedido>(RUTAS_COMPRAS.pagos.confirmar, cuerpo);
  }
}
