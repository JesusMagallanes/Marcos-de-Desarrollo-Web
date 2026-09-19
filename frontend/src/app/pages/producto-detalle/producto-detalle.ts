import { AyudaResena } from '../../shared/ia/ayuda-resena';
import { PreguntaProducto } from '../../shared/ia/pregunta-producto';
import { ImagenCaida } from '../../shared/imagen/imagen-caida';
import { Cargando } from '../../shared/cargando/cargando';
import { CarruselDescubrimiento } from '../../shared/descubrimiento';
import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { DatePipe, formatCurrency } from '@angular/common';
import { forkJoin, of } from 'rxjs';
import {
  AuthService,
  Carrusel,
  DescubrimientoService,
  CarritoService,
  ComparacionService,
  ErrorApi,
  IaService,
  nombreCompleto,
  Producto,
  ProductoService,
  ResumenResenas,
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
  imports: [RouterLink, DatePipe, Cargando, ImagenCaida, CarruselDescubrimiento, PreguntaProducto, AyudaResena],
  templateUrl: './producto-detalle.html',
  styleUrl: './producto-detalle.css',
})
export class ProductoDetalle implements OnDestroy {
  private ruta = inject(ActivatedRoute);
  private router = inject(Router);
  private productoService = inject(ProductoService);
  private valoracionService = inject(ValoracionService);
  private carrito = inject(CarritoService);
  private auth = inject(AuthService);
  private descubrimiento = inject(DescubrimientoService);
  private ia = inject(IaService);
  protected comparacion = inject(ComparacionService);
  protected avisoComparar = signal('');

  /** Añade o quita de la comparación; con tres ya elegidos, lo dice. */
  protected alternarComparar(p: Producto): void {
    const ok = this.comparacion.alternar({ id: p.id, name: p.name, imageUrl: p.imageUrl ?? null });
    this.avisoComparar.set(ok ? '' : 'Ya tienes 3 productos para comparar. Quita uno primero.');
  }

  /** Una pregunta sugerida se añade al borrador como pie para contestar. */
  protected usarIdeaResena(pregunta: string): void {
    const actual = this.comentario().trim();
    this.comentario.set((actual ? actual + '\n' : '') + pregunta + ' ');
  }

  protected cargando = signal(true);
  protected noEncontrado = signal(false);
  protected producto = signal<Producto | null>(null);

  /** Relacionados por contenido; null mientras no lleguen o si fallaron. */
  protected relacionados = signal<Carrusel | null>(null);

  /** Cuando se abrio la ficha actual, para medir la permanencia al salir. */
  private entradaEnFicha = 0;
  private fichaAbierta: { id: number; categoriaId?: number } | null = null;
  protected cantidad = signal(1);
  protected aviso = signal('');
  protected imagenActiva = signal(0);

  protected readonly estrellas = ESTRELLAS;
  protected autenticado = computed(() => this.auth.autenticado());

  /* ── valoraciones ── */
  protected valoraciones = signal<Valoracion[]>([]);
  /** Resumen con IA de las reseñas aprobadas; null si no hay IA o hay menos de tres. */
  protected resumenResenas = signal<ResumenResenas | null>(null);
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
  /*
   * `S/ 4299.00`, igual que en las tarjetas del catálogo. Antes aquí ponía
   * «S/.» y en las tarjetas «S/»: la misma tienda escribiendo su moneda de dos
   * maneras en dos pantallas seguidas.
   */
  protected precioMostrado = computed(() =>
    formatCurrency(this.producto()?.precioActual ?? this.producto()?.precio ?? 0, 'en-US', 'S/ ', 'PEN', '1.2-2'),
  );
  protected precioOriginalMostrado = computed(() =>
    formatCurrency(this.producto()?.precio ?? 0, 'en-US', 'S/ ', 'PEN', '1.2-2'),
  );

  /*
   * Iconos alojados en la tienda. Estaban enlazados a seeklogo, icons8,
   * wikipedia y una miniatura de Google Imágenes: cuando uno de esos sitios no
   * responde —o bloquea el hotlink—, la directiva de imagen caída los sustituye
   * por el marcador y la fila de pagos acaba enseñando cuadros grises.
   */
  protected readonly metodosPagoIconos = [
    { nombre: 'Yape', icono: '/Img/yape-icon.svg' },
    { nombre: 'Plin', icono: '/Img/plin-icon.svg' },
    { nombre: 'Visa', icono: '/Img/visa-icon.svg' },
    { nombre: 'Mastercard', icono: '/Img/mastercard-icon.svg' },
    { nombre: 'PayPal', icono: '/Img/paypal-icon.svg' },
  ];
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

  /**
   * Al salir de la ficha se manda el tiempo que estuvo abierta.
   *
   * <p>UNA sola vez, aqui. Un latido cada segundo daria el mismo dato con cien
   * veces mas peticiones, y el techo lo aplican tanto el servicio como el
   * backend: una pestana olvidada no es interes.
   */
  ngOnDestroy(): void {
    this.registrarSalida();
  }

  private registrarSalida(): void {
    const ficha = this.fichaAbierta;
    if (!ficha || this.entradaEnFicha === 0) {
      return;
    }
    this.descubrimiento.salidaDeProducto(
      ficha.id,
      ficha.categoriaId,
      Date.now() - this.entradaEnFicha,
    );
    this.fichaAbierta = null;
    this.entradaEnFicha = 0;
  }

  private cargar(id: number): void {
    // Navegar de un producto a otro no desmonta el componente: la salida de la
    // ficha anterior se registra aqui o se perderia.
    this.registrarSalida();

    this.cargando.set(true);
    this.noEncontrado.set(false);
    this.cantidad.set(1);
    this.imagenActiva.set(0);
    this.valoraciones.set([]);
    this.resumenResenas.set(null);
    this.miValoracion.set(null);
    this.valoracionesCargadas.set(false);

    this.productoService.obtener(id).subscribe({
      next: (p) => {
        this.producto.set(p);
        this.cargando.set(false);
        window.scrollTo({ top: 0 });
        this.cargarValoraciones(id);

        /*
         * ITEM_VIEW se registra AQUI y no cuando una tarjeta aparece en un
         * carrusel: abrir la ficha es una decision del usuario, que una tarjeta
         * se pinte no lo es. El servicio ignora la repeticion, asi que volver
         * atras y entrar de nuevo no cuenta como un interes nuevo.
         */
        this.descubrimiento.vistaDeProducto(p.id, p.categoriaId ?? undefined);
        this.entradaEnFicha = Date.now();
        this.fichaAbierta = { id: p.id, categoriaId: p.categoriaId ?? undefined };

        this.descubrimiento.similares(p.id, 12).subscribe((c) => this.relacionados.set(c));
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
        // Solo se pide si hay de qué: con menos de tres el backend no resume.
        if (lista.length >= 3) {
          this.ia.resumenResenas(id).subscribe((r) => this.resumenResenas.set(r));
        }
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
