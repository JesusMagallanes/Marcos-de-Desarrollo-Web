import { Cargando } from '../../../shared/cargando/cargando';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import {
  AdjuntoResponse,
  ColaboradorService,
  ErrorApi,
  EstadoSolicitud,
  SolicitudAdminResponse,
} from '../../../core';

/**
 * Bandeja de solicitudes para vender.
 *
 * <p>Lo que hace útil esta pantalla es poder comparar lo que alguien **escribió**
 * con lo que se ve en su documento. Por eso el visor abre el archivo al lado de
 * los datos y no en otra página.
 */
@Component({
  selector: 'app-admin-solicitudes',
  imports: [DatePipe, Cargando],
  templateUrl: './admin-solicitudes.html',
  styleUrl: './admin-solicitudes.css',
})
export class AdminSolicitudes implements OnInit, OnDestroy {
  private colaboradores = inject(ColaboradorService);

  protected cargando = signal(true);
  protected error = signal('');
  protected solicitudes = signal<SolicitudAdminResponse[]>([]);
  protected filtro = signal<EstadoSolicitud | ''>('PENDIENTE');

  /** La que está abierta en el panel de revisión. */
  protected abierta = signal<SolicitudAdminResponse | null>(null);
  protected resolviendo = signal(false);
  protected motivoRechazo = signal('');
  protected pidiendoMotivo = signal(false);

  /* ── Visor de documentos ── */

  protected adjuntoAbierto = signal<AdjuntoResponse | null>(null);
  protected urlAdjunto = signal<string | null>(null);
  protected cargandoAdjunto = signal(false);

  ngOnInit(): void {
    this.cargar();
  }

  /**
   * Las URLs de objeto ocupan memoria hasta que se revocan, y aquí lo que se
   * queda dentro son fotos de documentos de identidad. Se sueltan al salir.
   */
  ngOnDestroy(): void {
    this.soltarAdjunto();
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.colaboradores.bandeja(this.filtro() || undefined).subscribe({
      next: (s) => {
        this.solicitudes.set(s);
        this.cargando.set(false);
      },
      error: (e: ErrorApi) => {
        this.error.set(e.mensaje);
        this.cargando.set(false);
      },
    });
  }

  protected cambiarFiltro(estado: EstadoSolicitud | ''): void {
    this.filtro.set(estado);
    this.cerrar();
    this.cargar();
  }

  protected abrir(s: SolicitudAdminResponse): void {
    this.abierta.set(s);
    this.pidiendoMotivo.set(false);
    this.motivoRechazo.set('');
    this.soltarAdjunto();
  }

  protected cerrar(): void {
    this.abierta.set(null);
    this.soltarAdjunto();
  }

  /**
   * Abre un documento.
   *
   * <p>No se puede poner la ruta en un `src`: el endpoint exige el token y sin
   * la cabecera de autorización responde 401. Se pide con `HttpClient` y se
   * convierte en URL de objeto.
   */
  protected verAdjunto(a: AdjuntoResponse): void {
    if (!a.disponible) return;

    this.soltarAdjunto();
    this.adjuntoAbierto.set(a);
    this.cargandoAdjunto.set(true);

    this.colaboradores.descargarAdjunto(a.id).subscribe({
      next: (url) => {
        this.urlAdjunto.set(url);
        this.cargandoAdjunto.set(false);
      },
      error: (e: ErrorApi) => {
        this.cargandoAdjunto.set(false);
        this.adjuntoAbierto.set(null);
        this.error.set(e.mensaje);
      },
    });
  }

  protected soltarAdjunto(): void {
    const url = this.urlAdjunto();
    if (url) {
      URL.revokeObjectURL(url);
    }
    this.urlAdjunto.set(null);
    this.adjuntoAbierto.set(null);
  }

  protected esPdf(a: AdjuntoResponse): boolean {
    return this.colaboradores.esPdf(a);
  }

  protected tamano(bytes: number): string {
    return this.colaboradores.tamanoLegible(bytes);
  }

  /* ── Resolver ── */

  protected aprobar(): void {
    const s = this.abierta();
    if (!s || this.resolviendo()) return;

    this.resolviendo.set(true);
    this.colaboradores.aprobar(s.id).subscribe({
      next: () => {
        this.resolviendo.set(false);
        this.cerrar();
        this.cargar();
      },
      error: (e: ErrorApi) => {
        this.resolviendo.set(false);
        this.error.set(e.mensaje);
      },
    });
  }

  protected rechazar(): void {
    const s = this.abierta();
    if (!s || this.resolviendo()) return;

    // El motivo se le enseña al solicitante para que sepa qué corregir, así que
    // no vale cualquier cosa: el backend exige de 10 a 500 caracteres.
    if (this.motivoRechazo().trim().length < 10) {
      this.error.set('Explica el motivo en al menos 10 caracteres: el solicitante lo va a leer.');
      return;
    }

    this.resolviendo.set(true);
    this.colaboradores.rechazar(s.id, { motivo: this.motivoRechazo().trim() }).subscribe({
      next: () => {
        this.resolviendo.set(false);
        this.cerrar();
        this.cargar();
      },
      error: (e: ErrorApi) => {
        this.resolviendo.set(false);
        this.error.set(e.mensaje);
      },
    });
  }
}
