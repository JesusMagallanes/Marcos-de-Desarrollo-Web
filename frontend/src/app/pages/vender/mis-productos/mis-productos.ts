import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import {
  Categoria,
  CategoriaService,
  ErrorApi,
  Marca,
  MarcaService,
  Producto,
  ProductoService,
} from '../../../core';

/**
 * Los productos de un colaborador.
 *
 * <p>Lo que distingue esta pantalla del panel de administración es que aquí
 * **publicar no es publicar**: lo que se guarda queda pendiente de revisión y no
 * se ve en la tienda hasta que alguien lo aprueba. Editar algo ya aprobado lo
 * devuelve a la cola, y eso hay que decirlo antes de guardar, no después.
 */
@Component({
  selector: 'app-mis-productos',
  imports: [RouterLink, CurrencyPipe],
  templateUrl: './mis-productos.html',
  styleUrl: './mis-productos.css',
})
export class MisProductos implements OnInit {
  private productos = inject(ProductoService);
  private categorias = inject(CategoriaService);
  private marcas = inject(MarcaService);

  protected cargando = signal(true);
  protected guardando = signal(false);
  protected error = signal('');
  protected lista = signal<Producto[]>([]);
  protected catalogoCategorias = signal<Categoria[]>([]);
  protected catalogoMarcas = signal<Marca[]>([]);

  /** `null` = no hay formulario abierto; `0` = uno nuevo. */
  protected editando = signal<number | null>(null);

  protected nombre = signal('');
  protected descripcion = signal('');
  protected precio = signal('');
  protected stock = signal('');
  protected categoriaId = signal<number | null>(null);
  protected marcaId = signal<number | null>(null);
  protected imagenes = signal('');

  protected pendientes = computed(
    () => this.lista().filter((p) => p.estadoModeracion === 'PENDIENTE').length,
  );
  protected rechazados = computed(
    () => this.lista().filter((p) => p.estadoModeracion === 'RECHAZADO').length,
  );

  protected puedeGuardar = computed(
    () =>
      !this.guardando() &&
      this.nombre().trim().length >= 3 &&
      this.descripcion().trim().length > 0 &&
      Number(this.precio()) > 0 &&
      Number(this.stock()) >= 0 &&
      this.categoriaId() !== null,
  );

  ngOnInit(): void {
    this.cargar();
    this.categorias.listar().subscribe({ next: (c) => this.catalogoCategorias.set(c) });
    this.marcas.listar().subscribe({ next: (m) => this.catalogoMarcas.set(m) });
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.productos.mios().subscribe({
      next: (p) => {
        this.lista.set(p);
        this.cargando.set(false);
      },
      error: (e: ErrorApi) => {
        this.error.set(e.mensaje);
        this.cargando.set(false);
      },
    });
  }

  protected nuevo(): void {
    this.editando.set(0);
    this.nombre.set('');
    this.descripcion.set('');
    this.precio.set('');
    this.stock.set('');
    this.categoriaId.set(this.catalogoCategorias()[0]?.id ?? null);
    this.marcaId.set(null);
    this.imagenes.set('');
    this.error.set('');
  }

  protected editar(p: Producto): void {
    this.editando.set(p.id);
    this.nombre.set(p.name);
    this.descripcion.set(p.description);
    this.precio.set(String(p.precio));
    this.stock.set(String(p.stock));
    this.categoriaId.set(p.categoriaId);
    this.marcaId.set(p.marcaId);
    this.imagenes.set((p.imagenes ?? []).join('\n'));
    this.error.set('');
  }

  protected cancelar(): void {
    this.editando.set(null);
    this.error.set('');
  }

  protected guardar(): void {
    const id = this.editando();
    if (id === null || !this.puedeGuardar()) return;

    const dto = {
      name: this.nombre().trim(),
      description: this.descripcion().trim(),
      // El formulario no pide especificaciones: para un vendedor externo la
      // descripción basta, y un campo más es una barrera más.
      specifications: null,
      precio: Number(this.precio()),
      stock: Number(this.stock()),
      categoriaId: this.categoriaId()!,
      marcaId: this.marcaId(),
      imagenes: this.imagenes()
        .split('\n')
        .map((u) => u.trim())
        .filter(Boolean),
    };

    this.guardando.set(true);
    const peticion = id === 0 ? this.productos.crearMio(dto) : this.productos.actualizarMio(id, dto);

    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.editando.set(null);
        this.cargar();
      },
      error: (e: ErrorApi) => {
        this.guardando.set(false);
        this.error.set(e.mensaje);
      },
    });
  }

  protected eliminar(p: Producto): void {
    if (!confirm(`¿Eliminar "${p.name}"? No se puede deshacer.`)) return;

    this.productos.eliminarMio(p.id).subscribe({
      next: () => this.cargar(),
      error: (e: ErrorApi) => this.error.set(e.mensaje),
    });
  }
}
