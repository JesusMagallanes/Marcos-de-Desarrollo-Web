import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { IMAGEN_POR_DEFECTO, ImagenCaida } from './imagen-caida';

@Component({
  imports: [ImagenCaida],
  template: `<img [src]="url" alt="producto" />`,
})
class Anfitrion {
  url = 'https://tienda-de-otro.example/foto.jpg';
}

describe('ImagenCaida', () => {
  function montar() {
    const fixture = TestBed.createComponent(Anfitrion);
    fixture.detectChanges();
    const img: HTMLImageElement = fixture.nativeElement.querySelector('img');
    return { fixture, img };
  }

  it('sustituye por el marcador cuando la imagen no carga', () => {
    const { img } = montar();

    // Las imágenes de producto son URLs de otros sitios: cuando una deja de
    // responder, el navegador pinta el icono de imagen rota. El `|| '/Img/...'`
    // de las plantillas no lo cubre, porque la URL sí existe.
    img.dispatchEvent(new Event('error'));

    expect(img.getAttribute('src')).toBe(IMAGEN_POR_DEFECTO);
  });

  it('no entra en bucle si el propio marcador falla', () => {
    const { img } = montar();

    img.dispatchEvent(new Event('error'));
    expect(img.getAttribute('src')).toBe(IMAGEN_POR_DEFECTO);

    // Segundo fallo, ya con el marcador puesto: no se vuelve a asignar, que es
    // lo que dispararía otro `error` y así indefinidamente.
    img.dispatchEvent(new Event('error'));
    expect(img.getAttribute('src')).toBe(IMAGEN_POR_DEFECTO);
  });

  it('no toca nada mientras la imagen cargue bien', () => {
    const { img } = montar();

    expect(img.getAttribute('src')).toBe('https://tienda-de-otro.example/foto.jpg');
  });
});
