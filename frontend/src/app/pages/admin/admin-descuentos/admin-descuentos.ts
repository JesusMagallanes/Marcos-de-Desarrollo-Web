import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
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
  ProductoService,
} from '../../../core';

type Seccion = 'todos' | 'programado' | 'activo' | 'inactivo';

/**
 * Estado de un descuento:
 * - `programado`: hay descuento configurado pero aún no empieza.
 * - `activo`: el descuento está vigente ahora mismo.
 * - `inactivo`: sin descuento o ya vencido.
 */
function estadoDe(p: Producto): Seccion {
  if (p.precioOferta == null) return 'inactivo';
  if (p.enOferta) return 'activo';
  if (p.ofertaInicio && new Date(p.ofertaInicio).getTime() > Date.now()) return 'programado';
  return 'inactivo';
}

/**
 * Convierte un ISO del backend (instantáneo, p. ej. `2026-08-11T15:00:00Z`)
 * al formato `datetime-local` del navegador, en la zona horaria local.
 */
function aFechaLocal(iso: string | null): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** Convierte el `datetime-local` a ISO instantáneo con zona UTC (`…Z`). */
function aISO(datetimeLocal: string): string {
  if (!datetimeLocal) return '';
  return new Date(datetimeLocal).toISOString();
}

@Component({
  selector: 'app-admin-descuentos',
  imports: [ReactiveFormsModule],
  templateUrl: './admin-descuentos.html',
  styleUrl: '../admin-tabla.css',
})
export class AdminDescuentos implements OnInit, OnDestroy {
  private fb = inject(FormBuilder);
  private productoService = inject(ProductoService);
  private categoriaService = inject(CategoriaService);
  private marcaService = inject(MarcaService);

  protected estado = new EstadoPeticion();
  protected guardando = signal(false);

  protected productos = signal<Producto[]>([]);
  protected categorias = signal<Categoria[]>([]);
  protected marcas = signal<Marca[]>([]);

  protected filtroTexto = signal('');
  protected filtroCategoria = signal<number>(0);
  protected filtroMarca = signal<number>(0);
  protected seccion = signal<Seccion>('activo');

  protected seleccionadas = signal<Set<number>>(new Set());

  protected form = this.fb.nonNullable.group({
    tipo: ['PORCENTAJE', Validators.required],
    valor: [0, [Validators.required, Validators.min(0.01)]],
    inicio: ['', Validators.required],
    fin: ['', Validators.required],
  });

  protected visibles = computed(() => {
    const seccion = this.seccion();
    const texto = this.filtroTexto().trim().toLowerCase();
    const categoria = this.filtroCategoria();
    const marca = this.filtroMarca();

    return this.productos().filter((p) => {
      if (seccion !== 'todos' && estadoDe(p) !== seccion) return false;
      if (categoria && p.categoriaId !== categoria) return false;
      if (marca && p.marcaId !== marca) return false;
      if (
        texto &&
        !p.name.toLowerCase().includes(texto) &&
        !p.categoriaName?.toLowerCase().includes(texto) &&
        !p.marcaName?.toLowerCase().includes(texto)
      ) {
        return false;
      }
      return true;
    });
  });

  /** Cuántos productos hay en cada sección (sobre todo el catálogo). */
  protected conteos = computed(() => {
    const conteo: Record<Seccion, number> = { todos: 0, programado: 0, activo: 0, inactivo: 0 };
    for (const p of this.productos()) {
      conteo[estadoDe(p)]++;
      conteo.todos++;
    }
    return conteo;
  });

  protected seleccionadasVisibles = computed(() =>
    this.visibles().filter((p) => this.seleccionadas().has(p.id)),
  );

  protected todasVisiblesSeleccionadas = computed(
    () =>
      this.visibles().length > 0 &&
      this.visibles().every((p) => this.seleccionadas().has(p.id)),
  );

  protected conDescuentoSeleccionadas = computed(() =>
    this.seleccionadasVisibles().filter((p) => p.precioOferta != null),
  );

  /** Producto seleccionado para la vista previa; solo si hay exactamente uno. */
  protected productoVistaPrevia = computed<Producto | null>(() => {
    const [id] = this.seleccionadas();
    if (id === undefined) return null;
    return this.productos().find((x) => x.id === id) ?? null;
  });

  /** Resultado del descuento para el producto elegido; solo si hay uno seleccionado. */
  protected precioVistaPrevia = computed<number | null>(() => {
    const p = this.productoVistaPrevia();
    const valor = this.form.controls.valor.value;
    if (!p || !valor || valor <= 0) return null;

    return this.form.controls.tipo.value === 'MONTO'
      ? Math.max(0, Math.round((p.precio - valor) * 100) / 100)
      : Math.max(0, Math.round(p.precio * (1 - valor / 100) * 100) / 100);
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
      productos: this.productoService.recargar(),
      categorias: this.categoriaService.listar(),
      marcas: this.marcaService.listar(),
    }).subscribe({
      next: ({ productos, categorias, marcas }) => {
        this.productos.set(productos);
        this.categorias.set(categorias);
        this.marcas.set(marcas);
        this.seleccionadas.set(new Set());
        this.estado.exito();
      },
      error: (e: ErrorApi) => {
        this.estado.fallo(e);
        this.estado.exito();
      },
    });
  }

  protected alternarProducto(p: Producto, marcada: boolean): void {
    this.seleccionadas.update((s) => {
      const siguiente = new Set(s);
      if (marcada) siguiente.add(p.id);
      else siguiente.delete(p.id);
      return siguiente;
    });
  }

  protected alternarVisibles(): void {
    this.seleccionadas.update((s) => {
      const siguiente = new Set(s);
      const visibles = this.visibles();
      if (visibles.every((p) => siguiente.has(p.id))) {
        visibles.forEach((p) => siguiente.delete(p.id));
      } else {
        visibles.forEach((p) => siguiente.add(p.id));
      }
      return siguiente;
    });
  }

  protected seleccionarTodas(): void {
    this.seleccionadas.set(new Set(this.productos().map((p) => p.id)));
  }

  protected limpiarSeleccion(): void {
    this.seleccionadas.set(new Set());
  }

  /** Rellena el formulario con el descuento de un producto y lo deja seleccionado. */
  protected editarDescuento(p: Producto): void {
    this.seleccionadas.set(new Set([p.id]));
    this.form.controls.tipo.setValue(p.descuentoTipo === 'MONTO' ? 'MONTO' : 'PORCENTAJE');
    this.form.controls.valor.setValue(p.descuentoValor ?? 0);
    this.form.controls.inicio.setValue(aFechaLocal(p.ofertaInicio));
    this.form.controls.fin.setValue(aFechaLocal(p.ofertaFin));
  }

  protected aplicar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const ids = [...this.seleccionadas()];
    if (!ids.length) {
      this.estado.mostrarAviso('Selecciona al menos un producto.');
      return;
    }

    const valores = this.form.getRawValue();
    const inicioISO = aISO(valores.inicio);
    const finISO = aISO(valores.fin);
    if (!inicioISO || !finISO) {
      this.estado.mostrarAviso('Indica la fecha de inicio y de fin.');
      return;
    }
    if (valores.fin < valores.inicio) {
      this.estado.mostrarAviso('La fecha de fin debe ser posterior a la de inicio.');
      return;
    }

    this.guardando.set(true);
    this.estado.limpiarError();
    this.productoService
      .aplicarDescuento({
        productoIds: ids,
        tipo: valores.tipo as 'PORCENTAJE' | 'MONTO',
        valor: valores.valor,
        inicio: inicioISO,
        fin: finISO,
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.estado.mostrarAviso(`Descuento aplicado a ${ids.length} producto(s).`);
          this.cargar();
        },
        error: (e: ErrorApi) => {
          this.guardando.set(false);
          this.estado.fallo(e);
        },
      });
  }

  protected quitar(): void {
    const ids = [...this.seleccionadas()];
    if (!ids.length) {
      this.estado.mostrarAviso('Selecciona al menos un producto.');
      return;
    }

    this.guardando.set(true);
    this.estado.limpiarError();
    this.productoService.quitarDescuento({ productoIds: ids }).subscribe({
      next: () => {
        this.guardando.set(false);
        this.estado.mostrarAviso(`Descuento quitado de ${ids.length} producto(s).`);
        this.cargar();
      },
      error: (e: ErrorApi) => {
        this.guardando.set(false);
        this.estado.fallo(e);
      },
    });
  }

  protected invalido(campo: string): boolean {
    const c = this.form.get(campo);
    return !!c && c.invalid && c.touched;
  }

  protected seleccionTotal = computed(() => this.seleccionadas().size);
}
