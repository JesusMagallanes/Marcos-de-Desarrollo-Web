import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { forkJoin, of } from 'rxjs';
import {
  AuthService,
  CarritoService,
  ErrorApi,
  nombreCompleto,
  Producto,
  ProductoService,
  Valoracion,
  ValoracionService,
} from '../../core';

interface Especificacion {
  label: string;
  value: string;
}

/** Convierte el texto de especificaciones en pares etiqueta/valor. */
function especificacionesDe(texto: string | null | undefined): Especificacion[] {
  if (!texto) return [];
  const lista: Especificacion[] = [];
  const lineas = texto.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
  for (const linea of lineas) {
    const par = especificacionDeLinea(linea);
    if (par) lista.push(par);
  }
  return lista;
}

function especificacionDeLinea(linea: string): Especificacion | null {
  // Formato del admin: `· **Etiqueta**: Valor`. La viñeta puede ser `·`, `.`,
  // un guion, un asterisco o faltar; la negrita se descarta de la etiqueta.
  const idx = linea.indexOf(':');
  if (idx <= 0) return null;

  const label = linea
    .substring(0, idx)
    .trim()
    .replace(/^[·.\-*]\s*/, '')
    .replace(/\*\*/g, '')
    .trim();
  const value = linea.substring(idx + 1).trim();

  if (!label || !value) return null;
  return { label, value };
}

const ESTRELLAS = [1, 2, 3, 4, 5] as const;

@Component({
  selector: 'app-producto-detalle',
  imports: [RouterLink, DatePipe],
  templateUrl: './producto-detalle.html',
  styleUrl: './producto-detalle.css',
})
export class ProductoDetalle {
  private ruta = inject(ActivatedRoute);
  private router = inject(Router);
  private productoService = inject(ProductoService);
  private valoracionService = inject(ValoracionService);
  private carrito = inject(CarritoService);
  private auth = inject(AuthService);

  protected cargando = signal(true);
  protected noEncontrado = signal(false);
  protected producto = signal<Producto | null>(null);
  protected cantidad = signal(1);
  protected aviso = signal('');
  protected imagenActiva = signal(0);

  protected readonly estrellas = ESTRELLAS;
  protected autenticado = computed(() => this.auth.autenticado());

  /* ── valoraciones ── */
  protected valoraciones = signal<Valoracion[]>([]);
  protected miValoracion = signal<Valoracion | null>(null);
  protected valoracionesCargadas = signal(false);
  protected calificacion = signal(0);
  protected comentario = signal('');
  protected guardandoValoracion = signal(false);

  protected promedio = computed(() => {
    const lista = this.valoraciones();
    if (!lista.length) return 0;
    return Math.round((lista.reduce((suma, v) => suma + v.calificacion, 0) / lista.length) * 10) / 10;
  });

  protected redondeadoPromedio = computed(() => Math.round(this.promedio()));

  protected agotado = computed(() => (this.producto()?.stock ?? 0) <= 0);
  protected maximo = computed(() => Math.max(1, this.producto()?.stock ?? 1));

  protected enOferta = computed(() => this.producto()?.enOferta ?? false);
  protected precioMostrado = computed(() =>
    `S/. ${(this.producto()?.precioActual ?? this.producto()?.precio ?? 0).toFixed(2)}`,
  );
  protected precioOriginalMostrado = computed(() =>
    `S/. ${(this.producto()?.precio ?? 0).toFixed(2)}`,
  );
  protected etiquetaDescuento = computed(() => {
    const p = this.producto();
    if (!p?.enOferta || p.descuentoValor == null) return '';
    return p.descuentoTipo === 'MONTO' ? `-S/ ${p.descuentoValor}` : `-${p.descuentoValor}%`;
  });

  /** Galería del carrusel: la del producto, la principal como respaldo o la de relleno. */
  protected imagenesDetalle = computed(() => {
    const p = this.producto();
    if (!p) return [];
    const lista = (p.imagenes?.length ? p.imagenes : p.imageUrl ? [p.imageUrl] : []).filter(Boolean);
    return lista.length ? lista : ['/Img/img.png'];
  });

  /** Imagen mostrada en grande según la miniatura seleccionada. */
  protected imagenActivaSrc = computed(() => {
    const lista = this.imagenesDetalle();
    if (!lista.length) return '/Img/img.png';
    const idx = Math.min(this.imagenActiva(), lista.length - 1);
    return lista[idx] ?? '/Img/img.png';
  });

  protected seleccionarImagen(index: number): void {
    const total = this.imagenesDetalle().length;
    if (!total) return;
    const idx = ((index % total) + total) % total;
    this.imagenActiva.set(idx);
  }

  protected anterior(): void {
    this.seleccionarImagen(this.imagenActiva() - 1);
  }

  protected siguiente(): void {
    this.seleccionarImagen(this.imagenActiva() + 1);
  }

  protected especificaciones = computed<Especificacion[]>(() => {
    const p = this.producto();
    if (!p) return [];

    // Las especificaciones viven en su propio campo; si el producto aún no las
    // tiene (datos anteriores), se recorre la descripción como antes.
    const fuente = p.specifications?.trim() ? p.specifications : p.description;
    const lista = especificacionesDe(fuente);

    if (lista.length === 0) {
      if (p.categoriaName) lista.push({ label: 'Categoría', value: p.categoriaName });
      if (p.stock != null) lista.push({ label: 'Stock', value: String(p.stock) });
    }

    return lista.length ? lista : [{ label: '', value: 'Proximamente' }];
  });

  constructor() {
    this.ruta.paramMap.subscribe((params) => {
      const id = Number(params.get('id'));
      if (!id) {
        this.noEncontrado.set(true);
        this.cargando.set(false);
        return;
      }
      this.cargar(id);
    });
  }

  private cargar(id: number): void {
    this.cargando.set(true);
    this.noEncontrado.set(false);
    this.cantidad.set(1);
    this.imagenActiva.set(0);
    this.valoraciones.set([]);
    this.miValoracion.set(null);
    this.valoracionesCargadas.set(false);

    this.productoService.obtener(id).subscribe({
      next: (p) => {
        this.producto.set(p);
        this.cargando.set(false);
        window.scrollTo({ top: 0 });
        this.cargarValoraciones(id);
      },
      error: () => {
        this.noEncontrado.set(true);
        this.cargando.set(false);
      },
    });
  }

  private cargarValoraciones(id: number): void {
    forkJoin({
      lista: this.valoracionService.listar(id),
      mia: this.auth.autenticado() ? this.valoracionService.mia(id) : of(null),
    }).subscribe({
      next: ({ lista, mia }) => {
        this.valoraciones.set(lista);
        this.miValoracion.set(mia);
        this.valoracionesCargadas.set(true);
        if (mia) {
          this.calificacion.set(mia.calificacion);
          this.comentario.set(mia.comentario);
        }
      },
      error: () => this.valoracionesCargadas.set(true),
    });
  }

  protected seleccionarCalificacion(n: number): void {
    this.calificacion.set(n);
  }

  protected guardarValoracion(): void {
    const p = this.producto();
    const usuario = this.auth.usuario();
    const calificacion = this.calificacion();
    const comentario = this.comentario().trim();
    if (!p || !usuario) return;
    if (!calificacion) {
      this.mostrarAviso('Selecciona una calificación de 1 a 5 estrellas.');
      return;
    }
    if (!comentario) {
      this.mostrarAviso('Escribe un comentario antes de publicar.');
      return;
    }

    this.guardandoValoracion.set(true);
    this.valoracionService.guardar(p.id, { calificacion, comentario, nombre: nombreCompleto(usuario) }).subscribe({
      next: () => {
        this.guardandoValoracion.set(false);
        this.mostrarAviso('Gracias por tu valoración. Quedará visible cuando un administrador la apruebe.');
        this.cargarValoraciones(p.id);
      },
      error: (e: ErrorApi) => {
        this.guardandoValoracion.set(false);
        this.mostrarAviso(e.mensaje);
      },
    });
  }

  protected eliminarMiValoracion(): void {
    const p = this.producto();
    if (!p) return;

    this.guardandoValoracion.set(true);
    this.valoracionService.eliminar(p.id).subscribe({
      next: () => {
        this.guardandoValoracion.set(false);
        this.mostrarAviso('Valoración eliminada.');
        this.calificacion.set(0);
        this.comentario.set('');
        this.cargarValoraciones(p.id);
      },
      error: (e: ErrorApi) => {
        this.guardandoValoracion.set(false);
        this.mostrarAviso(e.mensaje);
      },
    });
  }

  protected onCantidadInput(event: Event): void {
    const valor = Number((event.target as HTMLInputElement).value);
    const nueva = Math.max(1, Math.min(this.maximo(), Number.isFinite(valor) ? valor : 1));
    this.cantidad.set(nueva);
  }

  protected agregarAlCarrito(): void {
    const p = this.producto();
    if (!p || this.agotado()) return;

    if (!this.auth.autenticado()) {
      this.router.navigate(['/login'], { queryParams: { redirigir: `/producto/${p.id}` } });
      return;
    }

    this.carrito.agregar(p.id, this.cantidad()).subscribe({
      next: () => this.mostrarAviso('Producto agregado al carrito.'),
      error: (e: ErrorApi) => this.mostrarAviso(e.mensaje),
    });
  }

  private mostrarAviso(texto: string): void {
    this.aviso.set(texto);
    setTimeout(() => this.aviso.set(''), 2800);
  }
}
