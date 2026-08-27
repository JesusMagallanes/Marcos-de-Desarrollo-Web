import { Cargando } from '../../../shared/cargando/cargando';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ErrorApi, EstadoPeticion, MetodoPago, MetodoPagoService, TipoPasarela } from '../../../core';

@Component({
  selector: 'app-admin-metodos-pago',
  imports: [ReactiveFormsModule, Cargando],
  templateUrl: './admin-metodos-pago.html',
  styleUrl: '../admin-tabla.css',
})
export class AdminMetodosPago implements OnInit, OnDestroy {
  private fb = inject(FormBuilder);
  private metodoPagoService = inject(MetodoPagoService);

  /** Cargando, error y aviso en un solo objeto; ver EstadoPeticion. */
  protected estado = new EstadoPeticion();
  protected guardando = signal(false);

  protected lista = signal<MetodoPago[]>([]);
  protected editandoId = signal<number | null>(null);
  protected formAbierto = signal(false);
  protected confirmandoId = signal<number | null>(null);

  /**
   * El tipo decide si el checkout cobra por pasarela o contra entrega, así que
   * se elige a mano. Sin este campo, todo método creado aquí nacía como OTRO y
   * el backend lo cerraba como pago en efectivo, aunque se llamara «MercadoPago».
   */
  protected readonly tipos: { valor: TipoPasarela; etiqueta: string }[] = [
    { valor: 'MERCADOPAGO', etiqueta: 'MercadoPago (pasarela online)' },
    { valor: 'EFECTIVO', etiqueta: 'Efectivo (se cobra al entregar)' },
    { valor: 'OTRO', etiqueta: 'Otro (se cobra al entregar)' },
  ];

  protected form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(50)]],
    description: ['', [Validators.required, Validators.maxLength(200)]],
    tipo: ['EFECTIVO' as TipoPasarela, [Validators.required]],
  });

  ngOnInit(): void {
    this.cargar();
  }

  ngOnDestroy(): void {
    this.estado.destruir();
  }

  private cargar(): void {
    this.estado.iniciar();
    this.metodoPagoService.listar().subscribe({
      next: (m) => {
        this.lista.set(m);
        this.estado.exito();
      },
      error: (e: ErrorApi) => this.estado.fallo(e),
    });
  }

  protected nuevo(): void {
    this.editandoId.set(null);
    this.form.reset({ name: '', description: '', tipo: 'EFECTIVO' });
    this.estado.limpiarError();
    this.formAbierto.set(true);
  }

  protected editar(m: MetodoPago): void {
    this.editandoId.set(m.id);
    this.form.setValue({ name: m.name, description: m.description, tipo: m.tipo });
    this.estado.limpiarError();
    this.formAbierto.set(true);
  }

  protected cerrar(): void {
    this.formAbierto.set(false);
    this.editandoId.set(null);
  }

  protected guardar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.guardando.set(true);
    const dto = this.form.getRawValue();
    const id = this.editandoId();

    const peticion = id
      ? this.metodoPagoService.actualizar(id, dto)
      : this.metodoPagoService.crear(dto);

    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.cerrar();
        this.estado.mostrarAviso(id ? 'Método actualizado.' : 'Método creado.');
        this.cargar();
      },
      error: (e: ErrorApi) => {
        this.guardando.set(false);
        this.estado.fallo(e);
      },
    });
  }

  protected eliminar(id: number): void {
    this.metodoPagoService.eliminar(id).subscribe({
      next: () => {
        this.confirmandoId.set(null);
        this.estado.mostrarAviso('Método eliminado.');
        this.cargar();
      },
      error: (e: ErrorApi) => {
        this.confirmandoId.set(null);
        this.estado.fallo(e);
      },
    });
  }

  protected invalido(campo: string): boolean {
    const c = this.form.get(campo);
    return !!c && c.invalid && c.touched;
  }
}
