import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { ImagenSubida, errorInterceptor } from '../../core';
import { SubirImagen } from './subir-imagen';

@Component({
  imports: [SubirImagen],
  template: `<app-subir-imagen (subida)="recibida.set($event)" />`,
})
class Anfitrion {
  recibida = signal<ImagenSubida | null>(null);
}

/** Un archivo con el tipo y el tamaño que se le pidan; el contenido da igual aquí. */
function archivo(nombre: string, tipo: string, bytes = 3): File {
  return new File([new Uint8Array(bytes)], nombre, { type: tipo });
}

describe('SubirImagen', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      // Con el interceptor real: el componente lee `ErrorApi`, no la respuesta cruda.
      providers: [provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  function montar() {
    const fixture = TestBed.createComponent(Anfitrion);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const selector: HTMLInputElement = el.querySelector('input[type=file]')!;
    const elegir = (f: File) => {
      Object.defineProperty(selector, 'files', { value: [f], configurable: true });
      selector.dispatchEvent(new Event('change'));
      fixture.detectChanges();
    };
    return { fixture, el, elegir, anfitrion: fixture.componentInstance };
  }

  it('sube la foto y avisa con la URL que devuelve el backend', () => {
    const { elegir, anfitrion, el, fixture } = montar();

    elegir(archivo('foto.jpg', 'image/jpeg'));
    expect(el.querySelector('button')?.textContent).toContain('Subiendo');

    http
      .expectOne({ url: '/api/productos/imagenes', method: 'POST' })
      .flush({ url: '/api/productos/imagenes/2026/09/x.jpg', tipoMime: 'image/jpeg', tamanoBytes: 3 });
    fixture.detectChanges();

    expect(anfitrion.recibida()?.url).toBe('/api/productos/imagenes/2026/09/x.jpg');
    expect(el.querySelector('button')?.textContent).toContain('Subir');
  });

  it('no sube lo que no es JPG, PNG o WebP, y lo dice', () => {
    const { elegir, el } = montar();

    elegir(archivo('logo.svg', 'image/svg+xml'));

    http.expectNone({ url: '/api/productos/imagenes', method: 'POST' });
    expect(el.querySelector('[role=alert]')?.textContent).toContain('JPG, PNG o WebP');
  });

  it('no sube más de 5 MB, sin gastar el viaje', () => {
    const { elegir, el } = montar();

    elegir(archivo('grande.png', 'image/png', 5 * 1024 * 1024 + 1));

    http.expectNone({ url: '/api/productos/imagenes', method: 'POST' });
    expect(el.querySelector('[role=alert]')?.textContent).toContain('5 MB');
  });

  it('enseña el motivo cuando el backend rechaza el archivo', () => {
    const { elegir, el, fixture, anfitrion } = montar();

    elegir(archivo('foto.jpg', 'image/jpeg'));
    http.expectOne({ url: '/api/productos/imagenes', method: 'POST' }).flush(
      { title: 'Datos inválidos', status: 400, detail: 'La imagen debe ser JPG, PNG o WebP.' },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();

    expect(anfitrion.recibida()).toBeNull();
    expect(el.querySelector('[role=alert]')?.textContent).toContain('JPG, PNG o WebP');
  });
});
