import { Cargando } from '../../../shared/cargando/cargando';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { forkJoin } from 'rxjs';
import {
  Categoria,
  CategoriaService,
  ConteosDescuento,
  ErrorApi,
  EstadoDescuento,
  EstadoPeticion,
  Marca,
  MarcaService,
  Producto,
  ProductoService,
} from '../../../core';

/*
 * La clasificación por estado del descuento vive ahora en el servidor.
 *
 * Estaba aquí, y para aplicarla hacía falta tener el catálogo COMPLETO en
 * memoria: la pantalla se descargaba cada producto de la tienda para filtrar
 * por cuatro criterios y contar cuántos había de cada tipo. Con unos miles de
 * productos son varios megas por abrir el panel.
 */
type Seccion = EstadoDescuento;

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
  imports: [ReactiveFormsModule, Cargando],
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

  /*
   * Lo que se enseña es lo que devolvió el servidor: los cuatro filtros van en
   * la consulta. Antes esto era un `filter` sobre el catálogo completo.
   */
  protected visibles = this.productos.asReadonly();

  /**
   * Cuántos hay en cada sección, sobre TODO el catálogo.
   *
   * <p>Los cuenta el servidor y llegan con la página. Deliberadamente NO
   * respetan los filtros de la pantalla: una pestaña sirve para saber cuánto
   * hay de cada cosa, y un número que baila al escribir en el buscador deja de
   * servir para eso. Es el mismo criterio que cuando se calculaban aquí.
   */
  protected conteos = signal<ConteosDescuento>({
    todos: 0,
    activo: 0,
    programado: 0,
    inactivo: 0,
  });

  /* ── Paginación ── */

  protected pagina = signal(0);
  protected totalPaginas = signal(0);
  protected totalFiltrado = signal(0);

  protected hayAnterior = computed(() => this.pagina() > 0);
  protected haySiguiente = computed(() => this.pagina() + 1 < this.totalPaginas());

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

  /** Filas por página en la tabla del panel. */
  private static readonly POR_PAGINA = 25;

  /* ── Filtros: cada cambio vuelve a la primera página y consulta ── */

  protected cambiarSeccion(seccion: Seccion): void {
    this.seccion.set(seccion);
    this.volverAPrimeraPagina();
  }

  protected buscarEnServidor(texto: string): void {
    this.filtroTexto.set(texto);
    this.volverAPrimeraPagina();
  }

  protected cambiarCategoria(id: number): void {
    this.filtroCategoria.set(id);
    this.volverAPrimeraPagina();
  }

  protected cambiarMarca(id: number): void {
    this.filtroMarca.set(id);
    this.volverAPrimeraPagina();
  }

  protected irA(pagina: number): void {
    if (pagina < 0 || pagina >= this.totalPaginas()) return;
    this.pagina.set(pagina);
    this.cargar();
  }

  /*
   * Cambiar un filtro vuelve a la página 0 a propósito: quedarse en la 4 tras
   * acotar la búsqueda deja al administrador mirando una tabla vacía sin saber
   * por qué.
   */
  private volverAPrimeraPagina(): void {
    this.pagina.set(0);
    this.cargar();
  }

  private cargar(): void {
    this.estado.iniciar();
    forkJoin({
      productos: this.productoService.paraDescuentos(
        {
          estado: this.seccion(),
          categoriaId: this.filtroCategoria() || null,
          marcaId: this.filtroMarca() || null,
          texto: this.filtroTexto().trim() || null,
        },
        this.pagina(),
        AdminDescuentos.POR_PAGINA,
      ),
      categorias: this.categoriaService.listar(),
      marcas: this.marcaService.listar(),
    }).subscribe({
      next: ({ productos, categorias, marcas }) => {
        this.productos.set(productos.content);
        this.conteos.set(productos.conteos);
        this.totalPaginas.set(productos.totalPages);
        this.totalFiltrado.set(productos.totalElements);
        this.categorias.set(categorias);
        this.marcas.set(marcas);
        /*
         * La selección se limpia al recargar, y eso importa: se aplican
         * descuentos en lote, así que arrastrar de una página a otra ids que ya
         * no están a la vista significaría tocar productos que el administrador
         * no está viendo.
         */
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
