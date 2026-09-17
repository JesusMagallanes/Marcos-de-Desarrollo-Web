import { Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { Params, Router } from '@angular/router';
import { ErrorApi, IMAGEN_TAMANO_MAXIMO, IMAGEN_TIPOS_ACEPTADOS, IaService } from '../../core';

/**
 * Buscar con una foto, desde la barra de búsqueda.
 *
 * La foto se envía, se describe y se descarta: no se guarda en ningún sitio.
 * El resultado es una búsqueda normal —con sus filtros si se pudieron
 * reconocer— que la persona ve en la URL y puede corregir.
 */
@Component({
  selector: 'app-buscar-foto',
  template: `
    @if (ia.disponible()) {
      <input #selector type="file" class="d-none" [accept]="acepta" (change)="alElegir($event)" aria-hidden="true" tabindex="-1" />
      <button type="button" class="btn boton-foto" [disabled]="buscando()" (click)="selector.click()"
        [attr.aria-label]="buscando() ? 'Buscando por foto' : 'Buscar con una foto'" [title]="error() || 'Buscar con una foto'">
        @if (buscando()) {
          <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
        } @else {
          <i class="fa-solid fa-camera" aria-hidden="true"></i>
        }
      </button>
    }
  `,
  styles: `
    :host { display: contents; }
    .boton-foto { color: #505050; border: none; background: transparent; }
    .boton-foto:hover { color: #06b56c; }
  `,
})
export class BuscarFoto {
  protected readonly ia = inject(IaService);
  private readonly router = inject(Router);
  private readonly selector = viewChild<ElementRef<HTMLInputElement>>('selector');

  protected readonly buscando = signal(false);
  protected readonly error = signal('');
  protected readonly acepta = IMAGEN_TIPOS_ACEPTADOS.join(',');

  protected alElegir(evento: Event): void {
    const entrada = evento.target as HTMLInputElement;
    const archivo = entrada.files?.[0];
    entrada.value = '';
    if (!archivo) return;
    if (!(IMAGEN_TIPOS_ACEPTADOS as readonly string[]).includes(archivo.type) || archivo.size > IMAGEN_TAMANO_MAXIMO) {
      this.error.set('La foto debe ser JPG, PNG o WebP y pesar menos de 5 MB.');
      return;
    }
    this.buscando.set(true);
    this.error.set('');
    this.ia.buscarPorFoto(archivo).subscribe({
      next: (r) => {
        this.buscando.set(false);
        if (!r) {
          this.error.set('No reconocí un producto en la foto.');
          return;
        }
        const b = r.busqueda;
        const q: Params = {};
        if (b?.precioMin != null) q['precioMin'] = b.precioMin;
        if (b?.precioMax != null) q['precioMax'] = b.precioMax;
        if (b?.marcaIds.length) q['marca'] = b.marcaIds;
        if (b?.atributos.length) q['atr'] = b.atributos;
        if (b?.categoriaSlug) {
          if (b.texto) q['q'] = b.texto;
          this.router.navigate(['/categoria', b.categoriaSlug], { queryParams: q });
        } else {
          q['q'] = b?.texto ?? r.consulta;
          this.router.navigate(['/buscar'], { queryParams: q });
        }
      },
      error: (e: ErrorApi) => {
        this.buscando.set(false);
        this.error.set(e.mensaje);
      },
    });
  }
}
