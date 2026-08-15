import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ColaboradorService } from './colaborador.service';
import { RUTAS_USUARIOS } from '../usuarios.routes';

/**
 * Lo que se prueba aquí no es "que llame a la URL", sino las tres cosas que
 * tienen truco y que el backend impone: que el archivo viaje como multipart,
 * que un 204 signifique "no hay solicitud" y no un error, y que un documento no
 * se pueda meter en un `src` porque hace falta el token.
 */
describe('ColaboradorService', () => {
  let servicio: ColaboradorService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(ColaboradorService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sube el archivo como multipart y con el tipo en la query', () => {
    const archivo = new File([new Uint8Array([0xff, 0xd8, 0xff])], 'dni.jpg', {
      type: 'image/jpeg',
    });

    servicio.subirAdjunto('DOCUMENTO_ANVERSO', archivo).subscribe();

    const req = http.expectOne(
      (r) =>
        r.url === RUTAS_USUARIOS.colaboradores.adjuntos &&
        r.params.get('tipo') === 'DOCUMENTO_ANVERSO',
    );

    expect(req.request.method).toBe('POST');
    // FormData, no JSON: el navegador tiene que poner el boundary de multipart.
    expect(req.request.body instanceof FormData).toBe(true);
    // Y sin Content-Type propio, o el boundary se pierde y el backend no parsea.
    expect(req.request.headers.get('Content-Type')).toBeNull();

    req.flush({ id: 1, tipo: 'DOCUMENTO_ANVERSO' });
  });

  it('un 204 en /mia se entrega como null, no como error', () => {
    // No haber solicitado nunca es un estado normal. Si esto se tratara como
    // fallo, la pantalla enseñaría un error rojo a quien solo quiere el
    // formulario.
    let recibido: unknown = 'sin-llamar';
    let hubo_error = false;

    servicio.miSolicitud().subscribe({
      next: (s) => (recibido = s),
      error: () => (hubo_error = true),
    });

    http
      .expectOne(RUTAS_USUARIOS.colaboradores.mia)
      .flush(null, { status: 204, statusText: 'No Content' });

    expect(hubo_error).toBe(false);
    expect(recibido).toBeNull();
  });

  it('el documento se pide como blob y se devuelve una URL de objeto', () => {
    // No vale poner la ruta en un <img src>: el endpoint exige el token y sin
    // la cabecera de autorización responde 401.
    let url = '';
    servicio.descargarAdjunto(7).subscribe((u) => (url = u));

    const req = http.expectOne(RUTAS_USUARIOS.colaboradores.adjunto(7));
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['bytes'], { type: 'image/jpeg' }));

    expect(url.startsWith('blob:')).toBe(true);
    // Se suelta: si no, cada documento abierto se queda en memoria.
    URL.revokeObjectURL(url);
  });

  it('dice qué adjuntos faltan según el tipo de solicitante', () => {
    const faltan = servicio.faltantes(
      ['DOCUMENTO_ANVERSO'],
      ['DOCUMENTO_ANVERSO', 'DOCUMENTO_REVERSO', 'FICHA_RUC'],
    );

    expect(faltan).toEqual(['DOCUMENTO_REVERSO', 'FICHA_RUC']);
  });

  it('el rechazo manda el motivo, que es lo que lee el solicitante', () => {
    servicio.rechazar(3, { motivo: 'La foto del reverso está movida.' }).subscribe();

    const req = http.expectOne(RUTAS_USUARIOS.colaboradores.rechazar(3));
    expect(req.request.body.motivo).toContain('movida');
    req.flush({});
  });
});
