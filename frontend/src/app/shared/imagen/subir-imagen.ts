import { Component, ElementRef, inject, input, output, signal, viewChild } from '@angular/core';
import {
  ErrorApi,
  IMAGEN_TAMANO_MAXIMO,
  IMAGEN_TIPOS_ACEPTADOS,
  ImagenSubida,
  ProductoService,
} from '../../core';

/**
 * Un botón que sube una foto de producto y avisa con la URL resultante.
 *
 * <p>Existe porque el panel solo admitía URLs pegadas de otros sitios, y eso
 * es lo que se rompe: el origen bloquea el enlace, borra la foto, o el service
 * worker no puede traerla. Con esto la foto queda alojada en la tienda y la URL
 * que se guarda es relativa y propia.
 *
 * <p>Sube de una en una y al momento, no al guardar el formulario: así una
 * validación fallida en un campo de texto no obliga a repetir varios megas, y
 * la miniatura se ve antes de guardar. Quien lo usa decide qué hacer con la
 * URL —rellenar una casilla, añadir una línea—; este componente no conoce el
 * formulario.
 *
 * <p>El tamaño y el tipo se comprueban aquí ANTES de subir para ahorrar el
 * viaje, pero la comprobación que manda es la del backend, que lee los bytes.
 */
@Component({
  selector: 'app-subir-imagen',
  templateUrl: './subir-imagen.html',
  styleUrl: './subir-imagen.css',
})
export class SubirImagen {
  private readonly productos = inject(ProductoService);
  private readonly selector = viewChild.required<ElementRef<HTMLInputElement>>('selector');

  /** Texto del botón; corto, porque suele ir dentro de un `input-group`. */
  readonly etiqueta = input('Subir');
  /** `sm` para encajar en los `input-group-sm` del panel. */
  readonly tamano = input<'sm' | 'md'>('sm');

  readonly subida = output<ImagenSubida>();

  protected readonly subiendo = signal(false);
  protected readonly error = signal('');
  protected readonly acepta = IMAGEN_TIPOS_ACEPTADOS.join(',');

  protected elegir(): void {
    this.error.set('');
    this.selector().nativeElement.click();
  }

  protected alElegir(evento: Event): void {
    const entrada = evento.target as HTMLInputElement;
    const archivo = entrada.files?.[0];
    // Se vacía siempre: si no, elegir el mismo archivo dos veces seguidas no
    // dispararía `change` y el segundo intento no haría nada.
    entrada.value = '';
    if (!archivo) {
      return;
    }
    if (!(IMAGEN_TIPOS_ACEPTADOS as readonly string[]).includes(archivo.type)) {
      this.error.set('Solo se admiten fotos JPG, PNG o WebP.');
      return;
    }
    if (archivo.size > IMAGEN_TAMANO_MAXIMO) {
      this.error.set('La foto pesa más de 5 MB.');
      return;
    }

    this.subiendo.set(true);
    this.productos.subirImagen(archivo).subscribe({
      next: (imagen) => {
        this.subiendo.set(false);
        this.subida.emit(imagen);
      },
      error: (e: ErrorApi) => {
        this.subiendo.set(false);
        this.error.set(this.explicar(e));
      },
    });
  }

  private explicar(e: ErrorApi): string {
    if (e.limitado) {
      return `Demasiadas subidas seguidas. Espera ${e.reintentarEn ?? 60} segundos.`;
    }
    if (e.sinPermiso) {
      return 'Tu cuenta no puede subir fotos.';
    }
    // 400 y 413 ya llegan redactados desde el backend («no es una imagen»,
    // «supera los 5 MB»); el resto, tal cual venga.
    return e.mensaje;
  }
}
