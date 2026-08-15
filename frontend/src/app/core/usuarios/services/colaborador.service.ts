import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { RUTAS_USUARIOS } from '../usuarios.routes';
import {
  AdjuntoResponse,
  EstadoSolicitud,
  RechazoRequest,
  SolicitudAdminResponse,
  SolicitudRequest,
  SolicitudResponse,
  SubidaResponse,
  TipoAdjunto,
} from '../models';

/**
 * Solicitudes para vender en la tienda — servicio `usuarios` (:8082).
 *
 * Contrato en `docs/contrato-colaboradores.md`.
 */
@Injectable({ providedIn: 'root' })
export class ColaboradorService {
  private readonly http = inject(HttpClient);

  /* ── Adjuntos ── */

  /**
   * POST /adjuntos — sube un archivo ANTES de enviar el formulario.
   *
   * Se sube de uno en uno, según se eligen, por dos motivos: así una validación
   * fallida en cualquier campo de texto no obliga a volver a mandar varios megas
   * de fotos, y el usuario ve al momento que el archivo se aceptó.
   *
   * **Subir dos veces el mismo `tipo` sustituye al anterior**, no lo añade: hay
   * que quedarse siempre con el último id devuelto.
   */
  subirAdjunto(tipo: TipoAdjunto, archivo: File): Observable<SubidaResponse> {
    const cuerpo = new FormData();
    cuerpo.append('archivo', archivo);

    // El navegador pone el Content-Type con el boundary de multipart; fijarlo a
    // mano lo rompe.
    return this.http.post<SubidaResponse>(RUTAS_USUARIOS.colaboradores.adjuntos, cuerpo, {
      params: new HttpParams().set('tipo', tipo),
    });
  }

  /**
   * GET /adjuntos/{id} — los bytes del archivo.
   *
   * Devuelve una URL de objeto lista para un `<img>` o un `<iframe>`. **No vale
   * poner la ruta directamente en un `src`**: el endpoint exige el token, y sin
   * la cabecera de autorización responde 401.
   *
   * Quien lo use tiene que llamar a `URL.revokeObjectURL` al cerrar el visor, o
   * las fotos de DNI se van acumulando en memoria.
   */
  descargarAdjunto(id: number): Observable<string> {
    return this.http
      .get(RUTAS_USUARIOS.colaboradores.adjunto(id), { responseType: 'blob' })
      .pipe(map((blob) => URL.createObjectURL(blob)));
  }

  /* ── Solicitante ── */

  /** POST /solicitudes — los adjuntos ya subidos se asocian solos. */
  solicitar(dto: SolicitudRequest): Observable<SolicitudResponse> {
    return this.http.post<SolicitudResponse>(RUTAS_USUARIOS.colaboradores.solicitudes, dto);
  }

  /**
   * GET /solicitudes/mia — la última, sea cual sea su estado.
   *
   * El backend responde **204 sin cuerpo** si nunca solicitó, que no es un
   * error: Angular lo entrega como `null` y así hay que tratarlo.
   */
  miSolicitud(): Observable<SolicitudResponse | null> {
    return this.http
      .get<SolicitudResponse>(RUTAS_USUARIOS.colaboradores.mia)
      .pipe(map((s) => s ?? null));
  }

  /* ── Administrador ── */

  /** GET /solicitudes — sin estado devuelve todas, las pendientes primero. */
  bandeja(estado?: EstadoSolicitud): Observable<SolicitudAdminResponse[]> {
    const params = estado ? new HttpParams().set('estado', estado) : undefined;
    return this.http.get<SolicitudAdminResponse[]>(RUTAS_USUARIOS.colaboradores.solicitudes, {
      params,
    });
  }

  /** POST /{id}/aprobar — cambia el rol del usuario en la misma transacción. */
  aprobar(id: number): Observable<SolicitudAdminResponse> {
    return this.http.post<SolicitudAdminResponse>(
      RUTAS_USUARIOS.colaboradores.aprobar(id),
      null,
    );
  }

  /** POST /{id}/rechazar — el motivo se le enseña al solicitante. */
  rechazar(id: number, dto: RechazoRequest): Observable<SolicitudAdminResponse> {
    return this.http.post<SolicitudAdminResponse>(
      RUTAS_USUARIOS.colaboradores.rechazar(id),
      dto,
    );
  }

  /** Los adjuntos que faltan para el tipo de persona elegido. */
  faltantes(subidos: TipoAdjunto[], exigidos: readonly TipoAdjunto[]): TipoAdjunto[] {
    return exigidos.filter((t) => !subidos.includes(t));
  }

  /** Sólo para mostrar: "1,2 MB". */
  tamanoLegible(bytes: number): string {
    return bytes < 1024 * 1024
      ? `${Math.round(bytes / 1024)} KB`
      : `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  /** Para el visor: si es PDF se abre en un marco, si no en una imagen. */
  esPdf(adjunto: AdjuntoResponse): boolean {
    return adjunto.tipoMime === 'application/pdf';
  }
}
