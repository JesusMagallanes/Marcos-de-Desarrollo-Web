import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { RUTAS_CATALOGO } from '../catalogo.routes';
import { LIMITES } from '../../shared/config/limites';
import { MensajeChat, RespuestaChat } from '../models';

/** Chatbot — servicio `catalogo` (:8081). Público, no requiere sesión. */
@Injectable({ providedIn: 'root' })
export class ChatbotService {
  private readonly http = inject(HttpClient);

  /**
   * POST /api/chatbot/mensaje Se recorta a la longitud que admite el backend para que un
   * texto largo devuelva respuesta en vez de un 400 de validación.
   */
  enviar(mensaje: string): Observable<RespuestaChat> {
    const cuerpo: MensajeChat = { mensaje: mensaje.trim().slice(0, LIMITES.maxMensajeChat) };
    return this.http.post<RespuestaChat>(RUTAS_CATALOGO.chatbot.mensaje, cuerpo);
  }
}
