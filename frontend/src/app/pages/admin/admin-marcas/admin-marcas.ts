import { Cargando } from '../../../shared/cargando/cargando';
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
  imports: [ReactiveFormsModule, Cargando],
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
    // Varias: una marca vende en mas de una categoria desde la migracion V18.
    // `Validators.required` no sirve sobre un array —[] pasa la validacion—, asi
    // que el minimo de uno se comprueba en `guardar()`.
    categoriaIds: [[] as number[]],
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

  /** Los nombres de las categorias de una marca, para la tabla. */
  protected nombresCategorias(m: Marca): string {
    const porId = new Map(this.categorias().map((c) => [c.id, c.name]));
    const nombres = (m.categoriaIds ?? []).map((id) => porId.get(id)).filter((n): n is string => !!n);
    return nombres.length ? nombres.join(', ') : '—';
  }

  /** Marca/desmarca una categoria en el formulario. */
  protected alternarCategoria(id: number, marcado: boolean): void {
    const actuales = this.form.controls.categoriaIds.value;
    this.form.controls.categoriaIds.setValue(
      marcado ? [...actuales, id] : actuales.filter((x) => x !== id),
    );
  }

  protected estaMarcada(id: number): boolean {
    return this.form.controls.categoriaIds.value.includes(id);
  }

  protected nuevo(): void {
    this.editandoId.set(null);
    this.form.reset({ name: '', descripcion: '', categoriaIds: [] });
    this.formAbierto.set(true);
  }

  protected editar(m: Marca): void {
    this.editandoId.set(m.id);
    this.form.setValue({
      name: m.name,
      descripcion: m.descripcion,
      categoriaIds: [...(m.categoriaIds ?? [])],
    });
    this.formAbierto.set(true);
  }

  protected cerrar(): void {
    this.formAbierto.set(false);
    this.editandoId.set(null);
  }

  protected guardar(): void {
    if (this.form.controls.categoriaIds.value.length === 0) {
      this.estado.fallo({ mensaje: 'Elige al menos una categoria.' } as ErrorApi);
      return;
    }
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
