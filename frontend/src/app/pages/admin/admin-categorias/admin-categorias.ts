import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Categoria, CategoriaService, ErrorApi, EstadoPeticion } from '../../../core';

/** Íconos listos para usar sin subir nada; viven en /Img del bundle. */
const ICONOS_PREDETERMINADOS = [
  '/Img/icon-laptop.svg',
  '/Img/icon-pc.svg',
  '/Img/icon-celular.svg',
  '/Img/icon-Phone.svg',
  '/Img/icon-consola.svg',
  '/Img/icon-game.svg',
  '/Img/icon-tag.svg',
];

/** Formatos que el canvas sabe redimensionar de forma segura. */
const MIME_PERMITIDOS = ['image/png', 'image/jpeg', 'image/webp', 'image/gif'];

const MAX_ARCHIVO = 2 * 1024 * 1024;

/** El ícono se reduce a este tamaño máximo por lado antes de guardarse. */
const MAX_LADO = 128;

@Component({
  selector: 'app-admin-categorias',
  imports: [ReactiveFormsModule],
  templateUrl: './admin-categorias.html',
  styleUrl: '../admin-tabla.css',
})
export class AdminCategorias implements OnInit, OnDestroy {
  private fb = inject(FormBuilder);
  private categoriaService = inject(CategoriaService);

  /** Cargando, error y aviso en un solo objeto; ver EstadoPeticion. */
  protected estado = new EstadoPeticion();
  protected guardando = signal(false);
  protected procesandoIcono = signal(false);
  protected errorIcono = signal('');
  protected categorias = signal<Categoria[]>([]);
  protected editandoId = signal<number | null>(null);
  protected formAbierto = signal(false);
  protected confirmandoId = signal<number | null>(null);

  protected readonly iconosPredeterminados = ICONOS_PREDETERMINADOS;

  protected form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    slug: ['', [Validators.required, Validators.pattern(/^[a-z0-9-]+$/)]],
    description: ['', [Validators.required, Validators.maxLength(500)]],
    urlImage: ['', [Validators.required]],
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
    this.form.reset({ name: '', slug: '', description: '', urlImage: '' });
    this.errorIcono.set('');
    this.formAbierto.set(true);
  }

  protected editar(c: Categoria): void {
    this.editandoId.set(c.id);
    this.form.setValue({
      name: c.name,
      slug: c.slug,
      description: c.description,
      // El backend admite urlImage nula; el formulario trabaja con cadena vacía.
      urlImage: c.urlImage ?? '',
    });
    this.errorIcono.set('');
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
    this.errorIcono.set('');
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

  /** Marca el ícono elegido y descarta el aviso de error anterior, si lo había. */
  protected elegirIcono(url: string): void {
    this.form.controls.urlImage.setValue(url);
    this.errorIcono.set('');
  }

  /** El valor guardado no es ninguno de los predeterminados: vino de una subida. */
  protected esPersonalizado(valor: string): boolean {
    return !!valor && !ICONOS_PREDETERMINADOS.includes(valor);
  }

  /** Lee el archivo, lo reduce a ícono (máx. 128px) y lo guarda como data URL. */
  protected subirIcono(input: HTMLInputElement): void {
    const archivo = input.files?.item(0);
    input.value = '';
    if (!archivo) return;

    if (!MIME_PERMITIDOS.includes(archivo.type)) {
      this.errorIcono.set('Formato no admitido: usa PNG, JPG, WebP o GIF.');
      return;
    }
    if (archivo.size > MAX_ARCHIVO) {
      this.errorIcono.set('El archivo supera los 2 MB.');
      return;
    }

    this.procesandoIcono.set(true);
    this.errorIcono.set('');
    this.aDataUrl(archivo)
      .then((url) => this.elegirIcono(url))
      .catch(() => this.errorIcono.set('No se pudo procesar la imagen. Prueba con otra.'))
      .finally(() => this.procesandoIcono.set(false));
  }

  /** Lee el archivo como data URL y lo pasa por el canvas para reducir el tamaño. */
  private aDataUrl(archivo: File): Promise<string> {
    return new Promise((resolver, rechazar) => {
      const lector = new FileReader();
      lector.onerror = () => rechazar();
      lector.onload = () => {
        const imagen = new Image();
        imagen.onerror = () => rechazar();
        imagen.onload = () => {
          try {
            resolver(this.redimensionar(imagen));
          } catch {
            rechazar();
          }
        };
        imagen.src = lector.result as string;
      };
      lector.readAsDataURL(archivo);
    });
  }

  private redimensionar(imagen: HTMLImageElement): string {
    const escala = Math.min(1, MAX_LADO / Math.max(imagen.width, imagen.height));
    const ancho = Math.max(1, Math.round(imagen.width * escala));
    const alto = Math.max(1, Math.round(imagen.height * escala));
    const lienzo = document.createElement('canvas');
    lienzo.width = ancho;
    lienzo.height = alto;
    const contexto = lienzo.getContext('2d');
    if (!contexto) throw new Error('Sin soporte de canvas');
    contexto.drawImage(imagen, 0, 0, ancho, alto);
    return lienzo.toDataURL('image/png');
  }

  protected invalido(campo: string): boolean {
    const c = this.form.get(campo);
    return !!c && c.invalid && c.touched;
  }
}
