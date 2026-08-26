import { HttpHeaders } from '@angular/common/http';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { ErrorApi, primerCampoInvalido, referenciaDeSoporte } from '../models';
import { errorInterceptor } from './error.interceptor';

/**
 * El interceptor es el único punto donde se traduce un fallo HTTP a algo que
 * la interfaz entiende. Si se rompe, los componentes muestran mensajes
 * genéricos y se pierde el detalle que redactó el backend.
 */
describe('errorInterceptor', () => {
  let http: HttpClient;
  let control: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    control = TestBed.inject(HttpTestingController);
  });

  /** Lanza una petición y devuelve el ErrorApi resultante. */
  function provocar(
    estado: number,
    cuerpo: object | string | null,
    cabeceras: Record<string, string> = {},
  ): Promise<ErrorApi> {
    return new Promise((resolver) => {
      http.get('/api/prueba').subscribe({ error: (e: ErrorApi) => resolver(e) });
      control.expectOne('/api/prueba').flush(cuerpo, {
        status: estado,
        statusText: 'error',
        headers: new HttpHeaders(cabeceras),
      });
    });
  }

  it('usa el mensaje que redactó el backend, no uno genérico', async () => {
    const error = await provocar(409, {
      title: 'Conflicto',
      status: 409,
      detail: "Solo quedan 2 unidades de 'Asus ROG'",
    });

    expect(error.mensaje).toBe("Solo quedan 2 unidades de 'Asus ROG'");
    expect(error.conflicto).toBe(true);
  });

  it('expone los errores por campo de un 400 de validación', async () => {
    const error = await provocar(400, {
      title: 'Datos inválidos',
      status: 400,
      detail: 'Revisa los campos',
      errores: { password: 'no debe estar vacío', emailAddress: 'formato incorrecto' },
    });

    expect(error.entradaInvalida).toBe(true);
    expect(error.camposInvalidos['password']).toBe('no debe estar vacío');
    expect(primerCampoInvalido(error)).toBeTruthy();
  });

  it('lee Retry-After en un 429 del limitador', async () => {
    const error = await provocar(
      429,
      { title: 'Demasiadas peticiones', status: 429, detail: 'Espera 892 segundos.' },
      { 'Retry-After': '892' },
    );

    expect(error.limitado).toBe(true);
    expect(error.reintentarEn).toBe(892);
    expect(error.transitorio).toBe(true);
  });

  it('un 429 sin Retry-After no inventa una espera', async () => {
    const error = await provocar(429, { title: 'x', status: 429, detail: 'y' });

    expect(error.limitado).toBe(true);
    expect(error.reintentarEn).toBeNull();
  });

  it('marca como transitorio lo que puede resolverse solo', async () => {
    expect((await provocar(503, null)).transitorio).toBe(true);
    expect((await provocar(0, null)).transitorio).toBe(true);
    expect((await provocar(429, null)).transitorio).toBe(true);

    // Un conflicto de negocio no se arregla repitiendo.
    expect((await provocar(409, null)).transitorio).toBe(false);
    expect((await provocar(400, null)).transitorio).toBe(false);
  });

  it('distingue 401 de 403', async () => {
    const sinSesion = await provocar(401, null);
    expect(sinSesion.noAutenticado).toBe(true);
    expect(sinSesion.sinPermiso).toBe(false);

    const sinPermiso = await provocar(403, null);
    expect(sinPermiso.sinPermiso).toBe(true);
    expect(sinPermiso.noAutenticado).toBe(false);
  });

  it('un 401 con cuerpo vacío sigue dando un mensaje útil', async () => {
    // Antes de estandarizar RespuestasSeguridad, el backend devolvía 401 sin
    // cuerpo. El interceptor debe seguir cubriendo ese caso.
    const error = await provocar(401, null);

    expect(error.mensaje).toBe('Necesitas iniciar sesión.');
  });

  it('conserva el identificador de correlación para soporte', async () => {
    const error = await provocar(
      500,
      { title: 'Error interno', status: 500, detail: 'Ocurrió un error inesperado' },
      { 'X-Correlation-Id': 'abc-123-def' },
    );

    expect(error.correlacionId).toBe('abc-123-def');
    expect(referenciaDeSoporte(error)).toContain('abc-123-def');
  });

  it('si un proxy duplica la cabecera, se queda con el primer valor', async () => {
    // El gateway y el servicio destino pueden acumularla: "abc-123, abc-123".
    const error = await provocar(500, null, { 'X-Correlation-Id': 'abc-123, abc-123' });

    expect(error.correlacionId).toBe('abc-123');
  });

  it('una red caída se normaliza igual que un error del servidor', async () => {
    const error = await provocar(0, null);

    expect(error.estado).toBe(0);
    expect(error.mensaje).toBe('No hay conexión con el servidor.');
    expect(error.servicioCaido).toBe(true);
  });

  it('un cuerpo que no es ProblemDetail no rompe la normalización', async () => {
    const error = await provocar(500, 'error en texto plano');

    expect(error.mensaje).toBe('Ocurrió un error en el servidor.');
    expect(error.camposInvalidos).toEqual({});
  });
});
