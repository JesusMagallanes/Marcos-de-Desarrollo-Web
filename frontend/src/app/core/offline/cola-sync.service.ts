import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ErrorApi } from '../shared/models';
import { CacheLecturaService } from './cache-lectura.service';
import { OperacionEncolada, bd, nuevoIdOperacion } from './db';

/** Lo que un servicio de dominio necesita para encolar una escritura. */
export interface PeticionEncolar {
  /** Clase de operación (`VALORACION_GUARDAR`); define qué cachés invalidar. */
  tipo: string;
  /** Entidad lógica tocada (`valoracion:42`): la clave anti-duplicado local. */
  claveEntidad: string;
  /** Endpoint de sincronización destino. */
  url: string;
  /** Cuerpo sin `operacionId`: lo añade el envío con el id real de la fila. */
  cuerpoBase: Record<string, unknown>;
}

/** Qué caché de lectura toca refrescar cuando cada tipo se confirma. */
const CACHE_POR_TIPO: Record<string, string> = {
  VALORACION_GUARDAR: 'valoraciones',
  VALORACION_ELIMINAR: 'valoraciones',
};

/** El error ya viene normalizado por el interceptor (tiene su huella digital). */
function esErrorApi(fallo: unknown): fallo is ErrorApi {
  return (
    typeof fallo === 'object' &&
    fallo !== null &&
    'transitorio' in fallo &&
    'camposInvalidos' in fallo
  );
}

/**
 * Decide si un fallo conviene reintentarlo (true) o es definitivo (false).
 *
 * <p>En producción el interceptor de errores ya entrega {@link ErrorApi}
 * clasificado; aquí se repite la lectura del status por si el error llega
 * crudo (pruebas, otro orden de interceptores): sin red, 408, 429 y 5xx son
 * de reintentar; el resto (400, 403, 409...) no.
 */
function falloTransitorio(fallo: unknown): boolean {
  if (esErrorApi(fallo)) {
    return fallo.transitorio;
  }
  if (fallo instanceof HttpErrorResponse) {
    const estado = fallo.status;
    return estado === 0 || estado === 408 || estado === 429 || estado >= 500;
  }
  return false;
}

/** El mejor mensaje humano disponible para enseñar en la bandeja. */
function mensajeFallo(fallo: unknown): string {
  if (esErrorApi(fallo)) {
    return fallo.mensaje;
  }
  if (fallo instanceof HttpErrorResponse) {
    return (
      (fallo.error as { detail?: string } | null)?.detail ??
      fallo.message ??
      'La operación fue rechazada'
    );
  }
  return 'La operación fue rechazada';
}

/**
 * Cola de escrituras offline.
 *
 * <p>El contrato con el usuario es simple: lo que escribe NUNCA se pierde por
 * una caída de red. La operación se guarda en IndexedDB ANTES de intentar
 * enviarla, y de ahí solo sale confirmada —o rechazada de forma definitiva—.
 *
 * <h2>Cómo se evitan duplicados</h2>
 *
 * <ul>
 *   <li><b>En local:</b> encolar sobre una entidad con operación pendiente
 *       SUSTITUYE esa pendiente (mismo {@code claveEntidad}) en vez de
 *       sumarse. Cinco ediciones seguidas de la misma reseña = un envío.</li>
 *   <li><b>Entre reintentos:</b> el {@code operacionId} nace al encolar y
 *       acompaña a la fila hasta su confirmación; todos los reenvíos llevan
 *       el mismo.</li>
 *   <li><b>En el servidor:</b> ese id choca contra la tabla
 *       {@code operaciones_aplicadas}, que responde "ya estaba" sin repetir
 *       el efecto. Es la última barrera y la única infalible: cubre el caso
 *       "el servidor SÍ aplicó pero la respuesta no volvió".</li>
 * </ul>
 */
@Injectable({ providedIn: 'root' })
export class ColaSyncService {
  private readonly http = inject(HttpClient);
  private readonly cache = inject(CacheLecturaService);

  private readonly sincronizandoSig = signal(false);
  private readonly pendientesSig = signal(0);
  private readonly rechazadasSig = signal<OperacionEncolada[]>([]);
  private readonly ultimoExitoSig = signal<number | null>(null);

  /** Hay un barrido en marcha; para no lanzar dos a la vez. */
  readonly sincronizando = this.sincronizandoSig.asReadonly();
  /** Operaciones aún sin confirmar (excluye las rechazadas definitivas). */
  readonly pendientes = this.pendientesSig.asReadonly();
  /** Operaciones que el servidor rechazó de forma definitiva: hay que corregirlas. */
  readonly rechazadas = this.rechazadasSig.asReadonly();
  readonly ultimoExito = this.ultimoExitoSig.asReadonly();

  /**
   * Encola una escritura y devuelve enseguida: la interfaz nunca espera a la
   * red para confirmar lo que el usuario ya escribió.
   *
   * <p>Si había una operación pendiente para la misma entidad, esta la
   * sustituye conservando SU operacionId: sigue siendo una sola operación
   * lógica cuyo último contenido manda.
   */
  async encolar(peticion: PeticionEncolar): Promise<void> {
    const pendiente = await bd.cola
      .where('claveEntidad')
      .equals(peticion.claveEntidad)
      .filter((op) => op.estado === 'PENDIENTE')
      .first();

    if (pendiente?.id !== undefined) {
      await bd.cola.update(pendiente.id, {
        url: peticion.url,
        cuerpo: peticion.cuerpoBase,
      });
    } else {
      await bd.cola.add({
        operacionId: nuevoIdOperacion(),
        tipo: peticion.tipo,
        claveEntidad: peticion.claveEntidad,
        url: peticion.url,
        cuerpo: peticion.cuerpoBase,
        creadoEn: Date.now(),
        intentos: 0,
        estado: 'PENDIENTE',
      });
    }

    await this.recargarContadores();
    // Si hay red sale ya mismo; si no, queda lista para la reconexión.
    void this.sincronizar();
  }

  /**
   * Envía las pendientes en orden FIFO.
   *
   * <p>Un fallo TRANSITORIO (red caída, 503, sesión por renovar) corta el
   * barrido y deja el resto intacto: las de detrás no pueden saltarse a la de
   * delante porque el orden ES parte del resultado. Un fallo DEFINITIVO (400,
   * 403, 409...) marca la operación RECHAZADA y continúa: reintentarla sería
   * eterno y taparía las que sí pueden salir.
   *
   * @returns true si la cola quedó vacía de pendientes
   */
  async sincronizar(): Promise<boolean> {
    if (this.sincronizandoSig() || !navigator.onLine || this.pendientesSig() === 0) {
      return this.pendientesSig() === 0;
    }

    this.sincronizandoSig.set(true);
    try {
      /*
       * El barrido se repite si llegaron operaciones NUEVAS mientras enviaba:
       * sin esto, una escritura encolada durante un barrido activo quedaba
       * huérfana hasta el próximo disparador (reconexión, otro encolado,
       * el temporizador de un minuto). Las que quedaron por un corte
       * transitorio NO re-disparan: ya están vistas y su turno volverá.
       */
      let hayNuevas = true;
      while (hayNuevas && navigator.onLine) {
        const operaciones = await bd.cola.where('estado').equals('PENDIENTE').sortBy('id');
        const vistas = new Set(operaciones.map((o) => o.id));

        for (const operacion of operaciones) {
          if (!navigator.onLine) {
            break;
          }

          try {
            const cuerpo = { ...operacion.cuerpo, operacionId: operacion.operacionId };
            const respuesta = await firstValueFrom(
              this.http.post<{ duplicado?: boolean }>(operacion.url, cuerpo),
            );

            if (respuesta === undefined) {
              /*
               * EMPTY sin error: el interceptor de sesión ya está llevando al
               * usuario al login. Tragar la operación aquí sería PERDERLA.
               */
              break;
            }

            await bd.cola.delete(operacion.id!);
            await this.invalidarSegun(operacion.tipo);
            this.ultimoExitoSig.set(Date.now());
          } catch (fallo) {
            if (falloTransitorio(fallo)) {
              await bd.cola.update(operacion.id!, {
                intentos: operacion.intentos + 1,
              });
              break;
            }

            await bd.cola.update(operacion.id!, {
              estado: 'RECHAZADA',
              intentos: operacion.intentos + 1,
              ultimoError: mensajeFallo(fallo),
            });
          }
        }

        if (!navigator.onLine) {
          break;
        }
        const pendientesAhora = await bd.cola.where('estado').equals('PENDIENTE').primaryKeys();
        hayNuevas = pendientesAhora.some((id) => !vistas.has(id as number));
      }
    } finally {
      this.sincronizandoSig.set(false);
      await this.recargarContadores();
    }

    return this.pendientesSig() === 0;
  }

  /** Vuelve a contar desde IndexedDB; única fuente de verdad. */
  async recargarContadores(): Promise<void> {
    const todas = await bd.cola.toArray();
    this.pendientesSig.set(todas.filter((op) => op.estado === 'PENDIENTE').length);
    this.rechazadasSig.set(
      todas.filter((op) => op.estado === 'RECHAZADA').sort((a, b) => a.id! - b.id!),
    );
  }

  private async invalidarSegun(tipo: string): Promise<void> {
    const prefijo = CACHE_POR_TIPO[tipo];
    if (prefijo) {
      await this.cache.invalidar(prefijo);
    }
  }
}
