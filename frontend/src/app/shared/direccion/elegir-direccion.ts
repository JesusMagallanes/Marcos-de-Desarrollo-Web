import { Component, HostListener, computed, inject, input, output, signal } from '@angular/core';
import { AuthService, DireccionEntrega, DireccionUsuario, direccionEnUnaLinea, direccionVacia } from '../../core';
import { DireccionModal } from './direccion-modal';

/**
 * «¿Te lo mandamos aquí, o a otro sitio?»
 *
 * <p>La dirección vive en el perfil, no en la tienda: se pone una vez y no se
 * reescribe en cada compra. Lo que se pregunta al pagar es solo la decisión —la
 * de siempre o una distinta— porque enviar a otro sitio es lo normal de vez en
 * cuando: un regalo, la oficina, la casa de los padres.
 *
 * <p>La dirección de una sola vez NO toca el perfil. Cambiar tu casa guardada
 * porque un día mandaste algo a la oficina es de las cosas que solo se
 * descubren cuando el pedido siguiente llega al sitio equivocado.
 */
@Component({
  selector: 'app-elegir-direccion',
  imports: [DireccionModal],
  templateUrl: './elegir-direccion.html',
  styleUrl: './direccion-modal.css',
})
export class ElegirDireccion {
  private auth = inject(AuthService);

  /** La que ya se eligió en este checkout, si se vuelve a abrir. */
  readonly elegida = input<DireccionEntrega | null>(null);

  readonly confirmar = output<DireccionEntrega>();
  readonly cerrar = output<void>();

  /** La guardada en el perfil. `null` mientras el usuario no tenga ninguna. */
  protected guardada = computed<DireccionUsuario | null>(() => this.auth.usuario()?.direccion ?? null);

  protected resumenGuardada = computed(() => {
    const d = this.guardada();
    return d ? direccionEnUnaLinea({ ...direccionVacia(), ...d }) : '';
  });

  /**
   * Se abre directamente en el editor cuando no hay nada guardado: sin dirección
   * no hay a dónde entregar, así que no tiene sentido preguntar «¿esta u otra?»
   * cuando no hay «esta».
   */
  protected editando = signal(false);

  protected mostrarEditor = computed(() => this.editando() || !this.guardada());

  /** Lo que ve el editor: lo ya elegido, o el perfil como punto de partida. */
  protected paraEditar = computed<DireccionEntrega | null>(() => {
    const yaElegida = this.elegida();
    if (yaElegida) return yaElegida;

    const u = this.auth.usuario();
    const d = this.guardada();
    if (!d) {
      // Sin dirección, al menos el receptor sale relleno: es quien compra.
      return u ? { ...direccionVacia(), ...this.receptorDelPerfil() } : null;
    }
    return { ...direccionVacia(), ...d, referencia: d.referencia ?? '', ...this.receptorDelPerfil() };
  });

  /** Enviar a la dirección del perfil, con los datos de contacto de la cuenta. */
  protected usarLaGuardada(): void {
    const d = this.guardada();
    if (!d) return;
    this.confirmar.emit({
      ...direccionVacia(),
      ...d,
      referencia: d.referencia ?? '',
      ...this.receptorDelPerfil(),
    });
  }

  protected usarOtra(): void {
    this.editando.set(true);
  }

  protected alGuardar(direccion: DireccionEntrega): void {
    this.confirmar.emit(direccion);
  }

  /**
   * Quien recibe, por defecto: el titular de la cuenta.
   *
   * <p>El perfil guarda un sitio, no un destinatario; el nombre y el teléfono
   * ya están en la cuenta y se usan como valor de partida. Siguen siendo
   * editables porque un regalo va a nombre de otro.
   */
  private receptorDelPerfil(): Pick<DireccionEntrega, 'receptorNombre' | 'telefonoContacto'> {
    const u = this.auth.usuario();
    return {
      receptorNombre: u ? [u.name, u.lastname].filter(Boolean).join(' ').trim() : '',
      telefonoContacto: u?.phoneNumber ?? '',
    };
  }

  @HostListener('document:keydown.escape')
  protected alPulsarEscape(): void {
    this.cerrar.emit();
  }
}
