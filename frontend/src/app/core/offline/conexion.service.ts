import { Injectable, computed, inject, signal } from '@angular/core';
import { CacheLecturaService } from './cache-lectura.service';
import { ColaSyncService } from './cola-sync.service';

/**
 * Estado de la conexión y motor de sincronización automática.
 *
 * <p>Centraliza lo que antes estaba regado por los componentes: ¿hay red?,
 * ¿quedan escrituras por confirmar?, ¿cuándo se reintentan? Tres disparadores
 * cubren todos los caminos razonables:
 *
 * <ul>
 *   <li>el evento `online` del navegador —la reconexión del usuario—,</li>
 *   <li>el arranque de la aplicación —cola heredada de una sesión anterior—,</li>
 *   <li>y un barrido periódico suave mientras haya pendientes: el evento
 *       online no siempre llega (suspender/reanudar, redes cautivas), y sin
 *       este respaldo una operación podía quedarse esperando para siempre.</li>
 * </ul>
 */
@Injectable({ providedIn: 'root' })
export class ConexionService {
  private readonly cola = inject(ColaSyncService);
  private readonly cache = inject(CacheLecturaService);

  private readonly conectadoSig = signal(
    typeof navigator === 'undefined' ? true : navigator.onLine,
  );

  /** True con red. El estado inicial es el que reporta el navegador. */
  readonly conectado = this.conectadoSig.asReadonly();
  /** Comodín para pintar "offline + N pendientes" en un solo vistazo. */
  readonly requiereAtencion = computed(() => !this.conectadoSig() || this.cola.pendientes() > 0);

  private iniciado = false;
  private barrido?: ReturnType<typeof setInterval>;

  /**
   * Registra oyentes y lanza el primer barrido.
   *
   * <p>Idempotente a propósito: lo llama el inicializador de la aplicación, y
   * que un segundo llamador no duplique oyentes ni temporizadores.
   */
  iniciar(): void {
    if (this.iniciado || typeof window === 'undefined') {
      return;
    }
    this.iniciado = true;

    window.addEventListener('online', this.alConectar);
    window.addEventListener('offline', this.alPerderRed);

    // Colas heredadas: la sesión anterior pudo cerrarse con operaciones fuera.
    void this.cache.limpiarVencidas();
    void this.cola.recargarContadores();
    void this.cola.sincronizar();

    // Respaldo periódico SOLO cuando hay trabajo: sin pendientes, cero tráfico.
    this.barrido = setInterval(() => {
      if (this.conectadoSig() && this.cola.pendientes() > 0) {
        void this.cola.sincronizar();
      }
    }, 60_000);
  }

  /** Sincroniza ahora; es lo que pide el botón manual del aviso. */
  sincronizarAhora(): Promise<boolean> {
    return this.cola.sincronizar();
  }

  private alConectar = (): void => {
    this.conectadoSig.set(true);
    void this.cola.sincronizar();
  };

  private alPerderRed = (): void => {
    this.conectadoSig.set(false);
  };
}
