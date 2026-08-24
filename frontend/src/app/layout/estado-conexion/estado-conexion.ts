import { Component, computed, inject } from '@angular/core';
import { ColaSyncService, ConexionService } from '../../core/offline';

/**
 * Aviso fijo de estado de conexión y sincronización.
 *
 * <p>Solo ocupa pantalla cuando hay algo que decir: sin conexión, o con
 * escrituras locales pendientes de confirmar. El resto del tiempo no existe
 * para el usuario — un aviso permanente de "todo bien" es ruido.
 */
@Component({
  selector: 'app-estado-conexion',
  imports: [],
  templateUrl: './estado-conexion.html',
  styleUrl: './estado-conexion.css',
})
export class EstadoConexion {
  private readonly conexion = inject(ConexionService);
  private readonly cola = inject(ColaSyncService);

  protected readonly conectado = this.conexion.conectado;
  protected readonly pendientes = this.cola.pendientes;
  protected readonly rechazadas = this.cola.rechazadas;
  protected readonly sincronizando = this.cola.sincronizando;

  /** El aviso offline tapa al de pendientes: primero está el problema grave. */
  protected readonly mensajeOffline = computed(
    () =>
      'Sin conexión. La tienda sigue funcionando con los datos guardados; ' +
      'tus cambios se enviarán solos al reconectar.',
  );

  protected sincronizarAhora(): void {
    void this.conexion.sincronizarAhora();
  }
}
