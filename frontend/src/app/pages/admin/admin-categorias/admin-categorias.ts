import { Cargando } from '../../../shared/cargando/cargando';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Categoria, CategoriaService, ErrorApi, EstadoPeticion, iconoCategoria } from '../../../core';

@Component({
  selector: 'app-admin-categorias',
  imports: [ReactiveFormsModule, Cargando],
  templateUrl: './admin-categorias.html',
  styleUrl: '../admin-tabla.css',
})
export class AdminCategorias implements OnInit, OnDestroy {
  private fb = inject(FormBuilder);
  private categoriaService = inject(CategoriaService);

  /** Cargando, error y aviso en un solo objeto; ver EstadoPeticion. */
  protected estado = new EstadoPeticion();
  protected guardando = signal(false);
  protected categorias = signal<Categoria[]>([]);
  protected editandoId = signal<number | null>(null);
  protected formAbierto = signal(false);
  protected confirmandoId = signal<number | null>(null);

  protected readonly iconoCategoria = iconoCategoria;

  protected form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    slug: ['', [Validators.required, Validators.pattern(/^[a-z0-9-]+$/)]],
    description: ['', [Validators.required, Validators.maxLength(500)]],
    icono: ['', [Validators.maxLength(60), Validators.pattern(/^[a-z0-9-]*$/)]],
  });

  ngOnInit(): void {
    this.cargar();
  }

  ngOnDestroy(): void {
    this.estado.destruir();
  }

  private cargar(): void {
    this.estado.iniciar();
    this.categoriaService.listar().subscribe({
      next: (c) => {
        this.categorias.set(c);
        this.estado.exito();
      },
      error: (e: ErrorApi) => {
        this.estado.fallo(e);
        this.estado.exito();
      },
    });
  }

  protected nuevo(): void {
    this.editandoId.set(null);
    this.form.reset({ name: '', slug: '', description: '', icono: '' });
    this.formAbierto.set(true);
  }

  protected editar(c: Categoria): void {
    this.editandoId.set(c.id);
    this.form.setValue({
      name: c.name,
      slug: c.slug,
      description: c.description,
      icono: c.icono ?? '',
    });
    this.formAbierto.set(true);
  }

  /** Sugiere el slug a partir del nombre mientras no se haya tocado a mano. */
  protected alEscribirNombre(valor: string): void {
    if (this.editandoId() !== null) return;
    const slug = valor
      .toLowerCase()
      .normalize('NFD')
      .replace(/[̀-ͯ]/g, '')
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/(^-|-$)/g, '');
    this.form.controls.slug.setValue(slug);
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
      ? this.categoriaService.actualizar(id, dto)
      : this.categoriaService.crear(dto);

    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.cerrar();
        this.estado.mostrarAviso(id ? 'Categoría actualizada.' : 'Categoría creada.');
        this.cargar();
      },
      error: (e: ErrorApi) => {
        this.guardando.set(false);
        this.estado.fallo(e);
      },
    });
  }

  protected eliminar(id: number): void {
    this.categoriaService.eliminar(id).subscribe({
      next: () => {
        this.confirmandoId.set(null);
        this.estado.mostrarAviso('Categoría eliminada.');
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
