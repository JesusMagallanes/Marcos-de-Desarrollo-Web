import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService, CarritoService, ErrorApi, Producto, ProductoService } from '../../core';

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

@Component({
  selector: 'app-producto-detalle',
  imports: [RouterLink],
  templateUrl: './producto-detalle.html',
  styleUrl: './producto-detalle.css',
})
export class ProductoDetalle {
  private ruta = inject(ActivatedRoute);
  private router = inject(Router);
  private productoService = inject(ProductoService);
  private carrito = inject(CarritoService);
  private auth = inject(AuthService);

  protected cargando = signal(true);
  protected noEncontrado = signal(false);
  protected producto = signal<Producto | null>(null);
  protected cantidad = signal(1);
  protected aviso = signal('');

  protected agotado = computed(() => (this.producto()?.stock ?? 0) <= 0);
  protected maximo = computed(() => Math.max(1, this.producto()?.stock ?? 1));

  protected precioMostrado = computed(() => `S/. ${(this.producto()?.precio ?? 0).toFixed(2)}`);

  /** Galería del carrusel: la del producto, la principal como respaldo o la de relleno. */
  protected imagenesDetalle = computed(() => {
    const p = this.producto();
    if (!p) return [];
    const lista = (p.imagenes?.length ? p.imagenes : p.imageUrl ? [p.imageUrl] : []).filter(Boolean);
    return lista.length ? lista : ['/Img/img.png'];
  });

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

    this.productoService.obtener(id).subscribe({
      next: (p) => {
        this.producto.set(p);
        this.cargando.set(false);
        window.scrollTo({ top: 0 });
      },
      error: () => {
        this.noEncontrado.set(true);
        this.cargando.set(false);
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
