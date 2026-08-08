import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService, ErrorApi, PATRON_TELEFONO, perfilIncompleto } from '../../../core';

@Component({
  selector: 'app-cuenta',
  imports: [ReactiveFormsModule],
  templateUrl: './cuenta.html',
})
export class Cuenta {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);

  protected guardando = signal(false);
  protected exito = signal('');
  protected error = signal('');

  protected form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2)]],
    lastname: ['', [Validators.required, Validators.minLength(2)]],
    emailAddress: ['', [Validators.required, Validators.email]],
    phoneNumber: ['', [Validators.required, Validators.pattern(PATRON_TELEFONO)]],
    address: ['', [Validators.required]],
  });

  /** Las cuentas de Google/Facebook llegan sin teléfono ni dirección. */
  protected datosIncompletos = signal(false);
  protected esCuentaSocial = signal(false);

  constructor() {
    const u = this.auth.usuario();
    if (u) {
      this.form.patchValue({
        name: u.name,
        lastname: u.lastname,
        emailAddress: u.emailAddress,
        phoneNumber: u.phoneNumber ?? '',
        address: u.address ?? '',
      });

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
        this.datosIncompletos.set(false);
        this.exito.set('¡Cambios guardados exitosamente!');
        setTimeout(() => this.exito.set(''), 3500);
      },
      error: (e: ErrorApi) => {
        this.guardando.set(false);
        this.error.set(e.mensaje);
      },
    });
  }

  protected invalido(campo: string): boolean {
    const c = this.form.get(campo);
    return !!c && c.invalid && c.touched;
  }
}
