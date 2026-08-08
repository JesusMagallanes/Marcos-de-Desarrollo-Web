import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { forkJoin } from 'rxjs';
import {
  Categoria,
  CategoriaService,
  ErrorApi,
  EstadoPeticion,
  Marca,
  MarcaService,
} from '../../../core';

@Component({
  selector: 'app-admin-marcas',
  imports: [ReactiveFormsModule],
  templateUrl: './admin-marcas.html',
  styleUrl: '../admin-tabla.css',
})
export class AdminMarcas implements OnInit, OnDestroy {
  private fb = inject(FormBuilder);
  private marcaService = inject(MarcaService);
  private categoriaService = inject(CategoriaService);

  /** Cargando, error y aviso en un solo objeto; ver EstadoPeticion. */
  protected estado = new EstadoPeticion();
  protected guardando = signal(false);
  protected marcas = signal<Marca[]>([]);
  protected categorias = signal<Categoria[]>([]);
  protected editandoId = signal<number | null>(null);
  protected formAbierto = signal(false);
  protected confirmandoId = signal<number | null>(null);

  protected form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    descripcion: ['', [Validators.required]],
    categoriaId: [0, [Validators.required, Validators.min(1)]],
  });

  ngOnInit(): void {
    this.cargar();
  }

  ngOnDestroy(): void {
    this.estado.destruir();
  }

  private cargar(): void {
    this.estado.iniciar();
    forkJoin({
      marcas: this.marcaService.listar(),
      categorias: this.categoriaService.listar(),
    }).subscribe({
      next: ({ marcas, categorias }) => {
        this.marcas.set(marcas);
        this.categorias.set(categorias);
        this.estado.exito();
      },
      error: (e: ErrorApi) => {
        this.estado.fallo(e);
        this.estado.exito();
      },
    });
  }

  protected nombreCategoria(id: number): string {
    return this.categorias().find((c) => c.id === id)?.name ?? '—';
  }

  protected nuevo(): void {
    this.editandoId.set(null);
    this.form.reset({ name: '', descripcion: '', categoriaId: 0 });
    this.formAbierto.set(true);
  }

  protected editar(m: Marca): void {
    this.editandoId.set(m.id);
    this.form.setValue({ name: m.name, descripcion: m.descripcion, categoriaId: m.categoriaId });
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

    const peticion = id ? this.marcaService.actualizar(id, dto) : this.marcaService.crear(dto);

    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.cerrar();
        this.estado.mostrarAviso(id ? 'Marca actualizada.' : 'Marca creada.');
        this.cargar();
      },
      error: (e: ErrorApi) => {
        this.guardando.set(false);
        this.estado.fallo(e);
      },
    });
  }

  protected eliminar(id: number): void {
    this.marcaService.eliminar(id).subscribe({
      next: () => {
        this.confirmandoId.set(null);
        this.estado.mostrarAviso('Marca eliminada.');
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
