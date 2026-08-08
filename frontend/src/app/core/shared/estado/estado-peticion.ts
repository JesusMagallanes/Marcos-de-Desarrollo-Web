import { computed, signal } from '@angular/core';
import { ErrorApi } from '../models/api-error.model';

/** Estado de una operación contra el backend: cargando, error y aviso de éxito. */
export class EstadoPeticion {
  private readonly cargandoSig = signal(false);
  private readonly errorSig = signal<ErrorApi | null>(null);
  private readonly avisoSig = signal('');

  private temporizador?: ReturnType<typeof setTimeout>;

  constructor(private readonly duracionAviso = 2800) {}

  readonly cargando = this.cargandoSig.asReadonly();
  readonly error = this.errorSig.asReadonly();
  readonly aviso = this.avisoSig.asReadonly();

  /** Mensaje listo para pintar; cadena vacía si no hay error. */
  readonly mensajeError = computed(() => this.errorSig()?.mensaje ?? '');

  /** Errores por campo que devolvió el backend en un 400 de validación. */
  readonly camposInvalidos = computed(() => this.errorSig()?.camposInvalidos ?? {});

  readonly hayError = computed(() => this.errorSig() !== null);

  /** Marca el inicio de una petición y limpia el error anterior. */
  iniciar(): void {
    this.cargandoSig.set(true);
    this.errorSig.set(null);
  }

  /** Fin correcto. Con `aviso` muestra un mensaje que se oculta solo. */
  exito(aviso?: string): void {
    this.cargandoSig.set(false);
    this.errorSig.set(null);
    if (aviso) {
      this.mostrarAviso(aviso);
    }
  }

  /** Fin con error. Recibe el {@link ErrorApi} que normalizó el interceptor. */
  fallo(error: ErrorApi): void {
    this.cargandoSig.set(false);
    this.errorSig.set(error);
  }

  /** Aviso suelto, sin que haya terminado ninguna petición. */
  mostrarAviso(texto: string): void {
    clearTimeout(this.temporizador);
    this.avisoSig.set(texto);
    this.temporizador = setTimeout(() => this.avisoSig.set(''), this.duracionAviso);
  }

  /** Descarta el error, por ejemplo al reabrir un formulario. */
  limpiarError(): void {
    this.errorSig.set(null);
  }

  /** Libera el temporizador pendiente; llámalo en ngOnDestroy si lo usas. */
  destruir(): void {
    clearTimeout(this.temporizador);
  }
}
