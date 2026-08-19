import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DireccionModal } from '../../../shared/direccion/direccion-modal';
import {
  AuthService,
  DireccionEntrega,
  DireccionUsuario,
  ErrorApi,
  PATRON_TELEFONO,
  direccionEnUnaLinea,
  direccionVacia,
  perfilIncompleto,
} from '../../../core';

@Component({
  selector: 'app-cuenta',
  imports: [ReactiveFormsModule, DireccionModal],
  templateUrl: './cuenta.html',
})
export class Cuenta {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);

  protected guardando = signal(false);
  protected exito = signal('');
  protected error = signal('');

  /*
   * La dirección NO está aquí. Son nueve campos y un mapa, y se editan en su
   * propio modal: metidos entre el nombre y el correo, cambiar de casa obligaba
   * a repasar todo lo demás.
   */
  protected form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2)]],
    lastname: ['', [Validators.required, Validators.minLength(2)]],
    emailAddress: ['', [Validators.required, Validators.email]],
    phoneNumber: ['', [Validators.required, Validators.pattern(PATRON_TELEFONO)]],
  });

  /** Las cuentas de Google/Facebook llegan sin teléfono ni dirección. */
  protected datosIncompletos = signal(false);
  protected esCuentaSocial = signal(false);

  /* ── Dirección de entrega ── */

  protected direccion = signal<DireccionUsuario | null>(null);
  protected direccionAbierta = signal(false);
  protected exitoDireccion = signal('');
  protected errorDireccion = signal('');

  protected resumenDireccion = computed(() => {
    const d = this.direccion();
    return d ? direccionEnUnaLinea({ ...direccionVacia(), ...d }) : '';
  });

  /** Lo que ve el modal al abrirse: lo guardado, o una hoja en blanco. */
  protected direccionParaEditar = computed<DireccionEntrega | null>(() => {
    const d = this.direccion();
    return d ? { ...direccionVacia(), ...d, referencia: d.referencia ?? '' } : null;
  });

  constructor() {
    const u = this.auth.usuario();
    if (u) {
      this.form.patchValue({
        name: u.name,
        lastname: u.lastname,
        emailAddress: u.emailAddress,
        phoneNumber: u.phoneNumber ?? '',
      });

      this.direccion.set(u.direccion);
      this.esCuentaSocial.set(u.proveedor !== 'LOCAL');
      this.datosIncompletos.set(perfilIncompleto(u));
    }
  }

  protected guardar(): void {
    const usuario = this.auth.usuario();
    if (!usuario || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.guardando.set(true);
    this.exito.set('');
    this.error.set('');

    this.auth.actualizarPerfil(usuario.id, this.form.getRawValue()).subscribe({
      next: () => {
        this.guardando.set(false);
        this.datosIncompletos.set(perfilIncompleto(this.auth.usuario()));
        this.exito.set('¡Cambios guardados exitosamente!');
        setTimeout(() => this.exito.set(''), 3500);
      },
      error: (e: ErrorApi) => {
        this.guardando.set(false);
        this.error.set(e.mensaje);
      },
    });
  }

  protected abrirDireccion(): void {
    this.exitoDireccion.set('');
    this.errorDireccion.set('');
    this.direccionAbierta.set(true);
  }

  /**
   * Guarda la dirección en el perfil.
   *
   * <p>El modal devuelve una dirección de entrega completa, pero aquí se guarda
   * solo el SITIO: el nombre y el teléfono ya están en la cuenta, y duplicarlos
   * garantiza que algún día digan cosas distintas.
   */
  protected guardarDireccion(entrega: DireccionEntrega): void {
    const usuario = this.auth.usuario();
    if (!usuario) return;

    const lugar: DireccionUsuario = {
      calle: entrega.calle,
      numero: entrega.numero,
      referencia: entrega.referencia || null,
      codigoPostal: entrega.codigoPostal,
      distrito: entrega.distrito,
      provincia: entrega.provincia,
      departamento: entrega.departamento,
      pais: entrega.pais ?? 'PE',
      latitud: entrega.latitud ?? null,
      longitud: entrega.longitud ?? null,
    };

    this.errorDireccion.set('');
    this.auth.guardarDireccion(usuario.id, lugar).subscribe({
      next: (u) => {
        this.direccion.set(u.direccion);
        this.datosIncompletos.set(perfilIncompleto(u));
        this.direccionAbierta.set(false);
        this.exitoDireccion.set('Dirección guardada.');
        setTimeout(() => this.exitoDireccion.set(''), 3500);
      },
      error: (e: ErrorApi) => this.errorDireccion.set(e.mensaje),
    });
  }

  protected invalido(campo: string): boolean {
    const c = this.form.get(campo);
    return !!c && c.invalid && c.touched;
  }
}
