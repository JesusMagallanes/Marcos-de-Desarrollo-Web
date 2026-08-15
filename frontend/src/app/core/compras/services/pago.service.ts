import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { RUTAS_COMPRAS } from '../compras.routes';
import { ConfirmarPagoRequest, DatosEntrega, Pedido, PreferenciaRequest, PreferenciaResponse } from '../models';

/** Checkout — servicio `compras` (:8083). Fachada de la saga de compra. */
@Injectable({ providedIn: 'root' })
export class PagoService {
  private readonly http = inject(HttpClient);

  /**
   * POST /api/pagos/preferencia Medio de pago y destino: el importe lo recalcula
   * el servidor releyendo el carrito, así que no se puede manipular desde el navegador.
   */
  crearPreferencia(metodoPagoId: number, entrega: DatosEntrega): Observable<PreferenciaResponse> {
    const cuerpo: PreferenciaRequest = {
      metodoPagoId,
      direccionEnvio: entrega.direccionEnvio.trim(),
      // Cadena vacía y `undefined` no son lo mismo para el backend: el campo es
      // opcional, así que si no hay referencia no se manda.
      referenciaEnvio: entrega.referenciaEnvio.trim() || undefined,
      telefonoContacto: entrega.telefonoContacto.trim(),
      latitud: entrega.latitud,
      longitud: entrega.longitud,
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
