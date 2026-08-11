import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { forkJoin } from 'rxjs';
import {
  Categoria,
  CategoriaService,
  ErrorApi,
  EstadoPeticion,
  Marca,
  MarcaService,
  Producto,
  ProductoRequest,
  ProductoService,
} from '../../../core';

@Component({
  selector: 'app-admin-productos',
  imports: [ReactiveFormsModule, CurrencyPipe],
  templateUrl: './admin-productos.html',
  styleUrl: '../admin-tabla.css',
})
export class AdminProductos implements OnInit, OnDestroy {
  private fb = inject(FormBuilder);
  private productoService = inject(ProductoService);
  private categoriaService = inject(CategoriaService);
  private marcaService = inject(MarcaService);

  /** Cargando, error y aviso en un solo objeto; ver EstadoPeticion. */
  protected estado = new EstadoPeticion();
  protected guardando = signal(false);

  protected productos = signal<Producto[]>([]);
  protected categorias = signal<Categoria[]>([]);
  protected marcas = signal<Marca[]>([]);

  protected filtro = signal('');
  protected editandoId = signal<number | null>(null);
  protected formAbierto = signal(false);
  protected confirmandoId = signal<number | null>(null);

  protected visibles = computed(() => {
    const q = this.filtro().trim().toLowerCase();
    if (!q) return this.productos();
    return this.productos().filter(
      (p) =>
        p.name.toLowerCase().includes(q) ||
        p.categoriaName?.toLowerCase().includes(q) ||
        p.marcaName?.toLowerCase().includes(q),
    );
  });

  /** Solo las marcas de la categoría elegida, como el select dependiente del admin viejo. */
  protected marcasDisponibles = computed(() => {
    const catId = this.form.controls.categoriaId.value;
    if (!catId) return this.marcas();
    return this.marcas().filter((m) => m.categoriaId === +catId);
  });

  protected form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(150)]],
    description: ['', [Validators.required, Validators.maxLength(500)]],
    specifications: ['', [Validators.maxLength(2000)]],
    precio: [0, [Validators.required, Validators.min(0.01)]],
    imagenes: this.fb.array([this.fb.control('', [Validators.maxLength(1000)])]),
    stock: [0, [Validators.required, Validators.min(0)]],
    categoriaId: [0, [Validators.required, Validators.min(1)]],
    marcaId: [0, [Validators.required, Validators.min(1)]],
  });

  protected get imagenesControles() {
    return this.form.controls.imagenes.controls;
  }

  protected agregarImagen(): void {
    this.form.controls.imagenes.push(this.fb.control('', [Validators.maxLength(1000)]));
  }

  protected quitarImagen(indice: number): void {
    this.form.controls.imagenes.removeAt(indice);
  }

  ngOnInit(): void {
    this.cargar();
  }

  ngOnDestroy(): void {
    this.estado.destruir();
  }

  private cargar(): void {
    this.estado.iniciar();
    forkJoin({
      productos: this.productoService.listar(),
      categorias: this.categoriaService.listar(),
      marcas: this.marcaService.listar(),
    }).subscribe({
      next: ({ productos, categorias, marcas }) => {
        this.productos.set(productos);
        this.categorias.set(categorias);
        this.marcas.set(marcas);
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
    this.form.reset({
      name: '',
      description: '',
      specifications: '',
      precio: 0,
      stock: 0,
      categoriaId: 0,
      marcaId: 0,
    });
    this.form.controls.imagenes.clear();
    this.agregarImagen();
    this.formAbierto.set(true);
  }

  protected editar(p: Producto): void {
    this.editandoId.set(p.id);
    this.form.setValue({
      name: p.name,
      description: p.description,
      // El backend admite specifications nula; el formulario trabaja con cadena vacía.
      specifications: p.specifications ?? '',
      precio: p.precio,
      imagenes: p.imagenes.length ? p.imagenes : [p.imageUrl ?? ''],
      stock: p.stock,
      categoriaId: p.categoriaId,
      marcaId: p.marcaId ?? 0,
    });
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
    this.estado.limpiarError();
    const valores = this.form.getRawValue();
    const id = this.editandoId();

    const dto: ProductoRequest = {
      name: valores.name,
      description: valores.description,
      specifications: valores.specifications,
      precio: valores.precio,
      imagenes: valores.imagenes.map((u: string | null) => (u ?? '').trim()).filter(Boolean),
      stock: valores.stock,
      categoriaId: valores.categoriaId,
      marcaId: valores.marcaId,
    };

    const peticion = id
      ? this.productoService.actualizar(id, dto)
      : this.productoService.crear(dto);

    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.cerrar();
        this.estado.mostrarAviso(id ? 'Producto actualizado.' : 'Producto creado.');
        this.cargar();
      },
      error: (e: ErrorApi) => {
        this.guardando.set(false);
        this.estado.fallo(e);
      },
    });
  }

  protected eliminar(id: number): void {
    this.productoService.eliminar(id).subscribe({
      next: () => {
        this.confirmandoId.set(null);
        this.estado.mostrarAviso('Producto eliminado.');
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
