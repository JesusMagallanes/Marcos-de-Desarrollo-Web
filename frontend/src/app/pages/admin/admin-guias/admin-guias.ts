import { Cargando } from '../../../shared/cargando/cargando';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  ErrorApi,
  EstadoPeticion,
  GuiaRequest,
  GuiaResumen,
  GuiaService,
  slugDesdeTitulo,
} from '../../../core';

/**
 * Gestión de las guías de "Aprende con nosotros".
 *
 * Los pasos se editan de uno en uno con un FormArray, igual que las imágenes de
 * producto. El orden en el que aparecen aquí es el orden en que los verá el
 * cliente; la posición la asigna el backend por el índice, así que basta con
 * subir o bajar filas.
 */
@Component({
  selector: 'app-admin-guias',
  imports: [ReactiveFormsModule, Cargando],
  templateUrl: './admin-guias.html',
  styleUrl: '../admin-tabla.css',
})
export class AdminGuias implements OnInit, OnDestroy {
  private fb = inject(FormBuilder);
  private guiaService = inject(GuiaService);

  protected estado = new EstadoPeticion();
  protected guardando = signal(false);

  protected guias = signal<GuiaResumen[]>([]);
  protected filtro = signal('');
  protected editandoId = signal<number | null>(null);
  protected formAbierto = signal(false);
  protected confirmandoId = signal<number | null>(null);
  /** El slug solo se autogenera mientras el admin no lo haya tocado a mano. */
  private slugManual = false;

  protected visibles = computed(() => {
    const q = this.filtro().trim().toLowerCase();
    if (!q) return this.guias();
    return this.guias().filter(
      (g) => g.titulo.toLowerCase().includes(q) || g.slug.toLowerCase().includes(q),
    );
  });

  protected form = this.fb.nonNullable.group({
    titulo: ['', [Validators.required, Validators.maxLength(160)]],
    slug: [
      '',
      [Validators.required, Validators.maxLength(140), Validators.pattern(/^[a-z0-9-]+$/)],
    ],
    resumen: ['', [Validators.required, Validators.maxLength(300)]],
    icono: ['', [Validators.maxLength(60), Validators.pattern(/^[a-z0-9-]*$/)]],
    posicion: [0, [Validators.required, Validators.min(0), Validators.max(999)]],
    publicada: [false],
    pasos: this.fb.array([this.nuevoPaso()]),
  });

  private nuevoPaso() {
    return this.fb.nonNullable.group({
      titulo: ['', [Validators.required, Validators.maxLength(160)]],
      descripcion: ['', [Validators.required, Validators.maxLength(2000)]],
    });
  }

  protected get pasos() {
    return this.form.controls.pasos;
  }

  protected agregarPaso(): void {
    this.pasos.push(this.nuevoPaso());
  }

  protected quitarPaso(indice: number): void {
    this.pasos.removeAt(indice);
  }

  /** Mover una fila cambia su orden de aparición en la tienda. */
  protected moverPaso(indice: number, salto: number): void {
    const destino = indice + salto;
    if (destino < 0 || destino >= this.pasos.length) return;

    const control = this.pasos.at(indice);
    this.pasos.removeAt(indice);
    this.pasos.insert(destino, control);
  }

  ngOnInit(): void {
    this.cargar();
  }

  ngOnDestroy(): void {
    this.estado.destruir();
  }

  private cargar(): void {
    this.estado.iniciar();
    this.guiaService.listarTodas().subscribe({
      next: (guias) => {
        this.guias.set(guias);
        this.estado.exito();
      },
      error: (e: ErrorApi) => {
        this.estado.fallo(e);
        this.estado.exito();
      },
    });
  }

  /** Autogenera el slug a partir del título mientras no se haya editado. */
  protected tituloCambiado(valor: string): void {
    if (!this.slugManual && !this.editandoId()) {
      this.form.controls.slug.setValue(slugDesdeTitulo(valor));
    }
  }

  protected slugTocado(): void {
    this.slugManual = true;
  }

  protected nuevo(): void {
    this.editandoId.set(null);
    this.slugManual = false;
    this.form.reset({
      titulo: '',
      slug: '',
      resumen: '',
      icono: '',
      posicion: this.siguientePosicion(),
      publicada: false,
    });
    this.pasos.clear();
    this.agregarPaso();
    this.formAbierto.set(true);
  }

  private siguientePosicion(): number {
    return this.guias().reduce((max, g) => Math.max(max, g.posicion), 0) + 1;
  }

  protected editar(guia: GuiaResumen): void {
    // El listado no trae los pasos: hay que pedir la guía completa.
    this.guiaService.obtenerParaEdicion(guia.slug).subscribe({
      next: (completa) => {
        this.editandoId.set(completa.id);
        this.slugManual = true;

        this.pasos.clear();
        completa.pasos.forEach(() => this.agregarPaso());

        this.form.setValue({
          titulo: completa.titulo,
          slug: completa.slug,
          resumen: completa.resumen,
          icono: completa.icono ?? '',
          posicion: completa.posicion,
          publicada: completa.publicada,
          pasos: completa.pasos.map((p) => ({ titulo: p.titulo, descripcion: p.descripcion })),
        });
        this.formAbierto.set(true);
      },
      error: (e: ErrorApi) => this.estado.fallo(e),
    });
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
    this.estado.limpiarError();

    const v = this.form.getRawValue();
    const id = this.editandoId();
    const dto: GuiaRequest = {
      slug: v.slug,
      titulo: v.titulo,
      resumen: v.resumen,
      icono: v.icono?.trim() || null,
      posicion: v.posicion,
      publicada: v.publicada,
      pasos: v.pasos.map((p) => ({ titulo: p.titulo, descripcion: p.descripcion })),
    };

    const peticion = id ? this.guiaService.actualizar(id, dto) : this.guiaService.crear(dto);

    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.cerrar();
        this.estado.mostrarAviso(id ? 'Guía actualizada.' : 'Guía creada.');
        this.cargar();
      },
      error: (e: ErrorApi) => {
        this.guardando.set(false);
        this.estado.fallo(e);
      },
    });
  }

  /** Publicar o retirar sin abrir el formulario completo. */
  protected alternarPublicada(guia: GuiaResumen): void {
    this.guiaService.obtenerParaEdicion(guia.slug).subscribe({
      next: (completa) => {
        this.guiaService
          .actualizar(completa.id, {
            slug: completa.slug,
            titulo: completa.titulo,
            resumen: completa.resumen,
            icono: completa.icono,
            posicion: completa.posicion,
            publicada: !completa.publicada,
            pasos: completa.pasos.map((p) => ({ titulo: p.titulo, descripcion: p.descripcion })),
          })
          .subscribe({
            next: () => {
              this.estado.mostrarAviso(
                completa.publicada ? 'Guía retirada del sitio.' : 'Guía publicada.',
              );
              this.cargar();
            },
            error: (e: ErrorApi) => this.estado.fallo(e),
          });
      },
      error: (e: ErrorApi) => this.estado.fallo(e),
    });
  }

  protected eliminar(id: number): void {
    this.guiaService.eliminar(id).subscribe({
      next: () => {
        this.confirmandoId.set(null);
        this.estado.mostrarAviso('Guía eliminada.');
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
