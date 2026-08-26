import Dexie, { Table } from 'dexie';

/**
 * Base de datos local del navegador (IndexedDB, vía Dexie).
 *
 * <p>Dos tablas, cada una con un trabajo claro:
 *
 * <ul>
 *   <li>{@code cache}: respuestas del backend reutilizadas para no volver a
 *       consultarlas. Cada entrada lleva su caducidad; sirve datos aunque la
 *       red falle, siempre que no hayan vencido del todo.</li>
 *   <li>{@code cola}: escrituras hechas sin conexión que esperan confirmación
 *       del servidor. FIFO por {@code id}; cada fila conserva su
 *       {@code operacionId} original entre reintentos, que es lo que evita
 *       duplicados al reenviar.</li>
 * </ul>
 *
 * <p>Aquí NO va nada sensible: ni tokens ni sesión (siguen en localStorage,
 * gestionados por AuthService) ni datos de otros usuarios. Solo catálogo
 * público y contenido propio del usuario autenticado.
 */

/** Una respuesta guardada, con su fecha de caducidad. */
export interface EntradaCache {
  /** Clave compuesta por dominio e identidad, p.ej. `productos:id:42`. */
  clave: string;
  valor: unknown;
  guardadoEn: number;
  /** Época en ms. Pasado este plazo la entrada se considera rancia. */
  expiraEn: number;
  /**
   * Generación del dominio al escribirse. Si no coincide con la generación
   * ACTUAL del dominio, la entrada está invalidada aunque su TTL siga vivo:
   * es lo que hace la invalidación inmune a carreras con lecturas.
   */
  generacion: number;
}

/** Ciclo de vida de una operación encolada. */
export type EstadoOperacion = 'PENDIENTE' | 'RECHAZADA';

/** Una escritura hecha en local que espera confirmación del servidor. */
export interface OperacionEncolada {
  /** Autoincremental: define el ORDEN de envío (FIFO). */
  id?: number;
  /**
   * UUID generado UNA vez al encolar. Viaja al servidor en cada reintento y
   * es la clave anti-duplicados: el servidor registra los ids aplicados y
   * responde "ya estaba" sin repetir el efecto.
   */
  operacionId: string;
  /** Qué clase de escritura es (`VALORACION_GUARDAR`, ...). Informativo. */
  tipo: string;
  /**
   * Identidad lógica de la entidad tocada (`valoracion:42`). Si hay una
   * operación pendiente para la misma entidad, un nuevo encolado LA SUSTITUYE
   * en vez de sumarse: el último contenido manda y se envía una sola vez.
   */
  claveEntidad: string;
  /** Endpoint de sincronización destino. */
  url: string;
  /**
   * Cuerpo SIN {@code operacionId}: el id real de la fila lo añade el envío.
   * Así sustituir el cuerpo nunca desincroniza el identificador.
   */
  cuerpo: Record<string, unknown>;
  creadoEn: number;
  intentos: number;
  estado: EstadoOperacion;
  /** Último mensaje de error recibido; solo para RECHAZADA. */
  ultimoError?: string;
}

class BaseLocal extends Dexie {
  cache!: Table<EntradaCache, string>;
  cola!: Table<OperacionEncolada, number>;

  constructor() {
    super('smartzone-offline');
    this.version(1).stores({
      // Índice por caducidad para purgar barato.
      cache: 'clave, expiraEn',
      // &operacionId = único; consultas frecuentes por entidad y estado.
      cola: '++id, &operacionId, claveEntidad, estado',
    });
  }
}

export const bd = new BaseLocal();

/** UUID v4 con reserva: contextos inseguros no tienen crypto.randomUUID. */
export function nuevoIdOperacion(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return `${Date.now().toString(16)}-${Math.random().toString(16).slice(2, 10)}-${Math.random()
    .toString(16)
    .slice(2, 10)}-${Math.random().toString(16).slice(2, 10)}-${Math.random()
    .toString(16)
    .slice(2, 14)}`.padEnd(36, '0');
}
