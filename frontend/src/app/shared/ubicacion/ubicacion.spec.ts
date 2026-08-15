import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { Coordenadas, Ubicacion, extraerCoordenadas, teselasAlrededor } from './ubicacion';

/**
 * Google Maps reparte las coordenadas en varios formatos segun de donde se copie
 * el enlace. Exigirle al usuario que acierte con uno seria trasladarle un
 * problema nuestro, asi que se aceptan todos los que aparecen en la practica.
 */
describe('extraerCoordenadas', () => {
  it('del enlace de la barra de direcciones (@lat,lng)', () => {
    const c = extraerCoordenadas('https://www.google.com/maps/@-12.046374,-77.042793,17z');
    expect(c).toEqual({ latitud: -12.046374, longitud: -77.042793 });
  });

  it('del enlace de "compartir" (?q=)', () => {
    const c = extraerCoordenadas('https://maps.google.com/?q=-12.121,-77.029');
    expect(c).toEqual({ latitud: -12.121, longitud: -77.029 });
  });

  it('del enlace largo (!3d !4d)', () => {
    const c = extraerCoordenadas('https://www.google.com/maps/place/X/@-1.5,-2.5,17z/data=!3d-12.05!4d-77.04');
    expect(c).toEqual({ latitud: -12.05, longitud: -77.04 });
  });

  it('de un par pegado a mano', () => {
    expect(extraerCoordenadas('-12.046374, -77.042793')).toEqual({
      latitud: -12.046374,
      longitud: -77.042793,
    });
  });

  it('un enlace corto no trae coordenadas: se rechaza en vez de inventarlas', () => {
    // No se pueden resolver sin llamar a Google. Devolver null es lo que hace
    // que la interfaz pida el enlace largo en vez de fallar sin explicar.
    expect(extraerCoordenadas('https://maps.app.goo.gl/AbCdEf123')).toBeNull();
  });

  it('un texto cualquiera tampoco', () => {
    expect(extraerCoordenadas('Av. Los Proceres 1420')).toBeNull();
    expect(extraerCoordenadas('')).toBeNull();
  });

  it('descarta valores fuera del rango de la Tierra', () => {
    // Tienen la forma de una coordenada pero no lo son: es otra cosa.
    expect(extraerCoordenadas('999.5, -77.0')).toBeNull();
    expect(extraerCoordenadas('-12.0, 500.5')).toBeNull();
  });
});

/**
 * El componente en sí. Lo que se prueba es lo que el usuario ve: que hay mapa
 * cuando hay punto, que no lo hay cuando no, y que en modo revisión no aparece
 * ningún botón para tocarlo.
 */
describe('Ubicacion', () => {
  function montar(coordenadas: Coordenadas | null, soloLectura = false): HTMLElement {
    TestBed.configureTestingModule({ imports: [Ubicacion] });
    const fixture = TestBed.createComponent(Ubicacion);
    fixture.componentRef.setInput('coordenadas', coordenadas);
    fixture.componentRef.setInput('soloLectura', soloLectura);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('con un punto, enseña el mapa centrado en él', () => {
    const el = montar({ latitud: -12.046374, longitud: -77.042793 });

    // El mapa son imágenes y no un iframe porque la CSP declara frame-src
    // 'none'. Si alguien lo cambia por un iframe, esta prueba lo dice.
    const teselas = [...el.querySelectorAll('img')];
    expect(teselas.length).toBe(9);
    expect(teselas[0].getAttribute('src')).toMatch(
      /^https:\/\/tile\.openstreetmap\.org\/16\/\d+\/\d+\.png$/,
    );
    expect(el.querySelector('a')?.getAttribute('href')).toContain(
      'google.com/maps?q=-12.046374,-77.042793',
    );
  });

  it('sin punto, ofrece las dos formas de ponerlo', () => {
    const el = montar(null);
    const textos = [...el.querySelectorAll('button')].map((b) => b.textContent?.trim());

    expect(el.querySelector('img')).toBeNull();
    expect(textos.some((t) => t?.includes('Usar mi ubicación'))).toBe(true);
    expect(textos.some((t) => t?.includes('Pegar enlace'))).toBe(true);
  });

  it('en solo lectura no hay nada que tocar', () => {
    // Quien revisa una solicitud necesita ver el sitio para decidir, pero mover
    // el punto de otro no le corresponde.
    const el = montar({ latitud: -12.05, longitud: -77.04 }, true);

    expect(el.querySelector('img')).not.toBeNull();
    expect(el.querySelectorAll('button').length).toBe(0);
  });

  it('en solo lectura y sin punto, lo dice en vez de quedarse en blanco', () => {
    // Un hueco vacío no distingue "no lo marcó" de "falla la pantalla".
    const el = montar(null, true);

    expect(el.textContent).toContain('Sin ubicación');
    expect(el.querySelectorAll('button').length).toBe(0);
  });
});

/**
 * La unica parte con matematicas. Se prueba aparte porque equivocarse en un
 * signo no se ve como un error: se ve como un mapa de otro continente, y eso
 * puede pasar desapercibido mucho tiempo.
 */
describe('teselasAlrededor', () => {
  it('el punto cae dentro de la tesela del centro', () => {
    const { teselas, centro } = teselasAlrededor({ latitud: -12.046374, longitud: -77.042793 });

    // La cuadricula es de 3x3 y la del medio empieza en 256: si el punto cayera
    // fuera de esa franja, el mapa estaria descentrado.
    expect(teselas.length).toBe(9);
    expect(centro.x).toBeGreaterThanOrEqual(256);
    expect(centro.x).toBeLessThan(512);
    expect(centro.y).toBeGreaterThanOrEqual(256);
    expect(centro.y).toBeLessThan(512);
  });

  it('Lima y Tokio no dan la misma tesela', () => {
    // Una proyeccion mal escrita suele devolver siempre lo mismo o el espejo.
    const lima = teselasAlrededor({ latitud: -12.046374, longitud: -77.042793 }).teselas[4].url;
    const tokio = teselasAlrededor({ latitud: 35.6762, longitud: 139.6503 }).teselas[4].url;

    expect(lima).not.toBe(tokio);
  });

  it('el hemisferio norte queda por encima del sur', () => {
    // En estas coordenadas la Y crece hacia el sur. Si se invirtiera, el mapa
    // saldria del revés y nadie reconoceria su barrio.
    const norte = teselasAlrededor({ latitud: 40, longitud: 0 }).teselas[4].url;
    const sur = teselasAlrededor({ latitud: -40, longitud: 0 }).teselas[4].url;
    const fila = (u: string) => Number(u.split('/').pop()!.replace('.png', ''));

    expect(fila(norte)).toBeLessThan(fila(sur));
  });

  it('en el meridiano 180 se da la vuelta en vez de pedir teselas que no existen', () => {
    const { teselas } = teselasAlrededor({ latitud: 0, longitud: 179.999 });
    const columnas = teselas.map((t) => Number(t.url.split('/')[4]));

    // 2^16 = 65536 columnas: la siguiente a la ultima es la 0, no la 65536.
    expect(Math.max(...columnas)).toBeLessThan(65536);
    expect(columnas).toContain(0);
  });

  it('cerca del polo se descartan las filas que no existen', () => {
    // Arriba del todo no hay mapa: pedir esas teselas daria 404 y un hueco gris.
    // 85.0511 es el borde de la proyeccion: por encima no hay mundo que pintar.
    const { teselas } = teselasAlrededor({ latitud: 85.0511, longitud: 0 });

    expect(teselas.length).toBe(6);
    expect(teselas.every((t) => Number(t.url.split('/').pop()!.replace('.png', '')) >= 0)).toBe(true);
  });
});
