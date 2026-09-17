import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { errorInterceptor } from '../../shared/interceptors';
import { IaService } from './ia.service';

describe('IaService', () => {
  let servicio: IaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(IaService);
    http = TestBed.inject(HttpTestingController);
  });

  it('pregunta si hay IA al crearse, y hasta saberlo no la da por disponible', () => {
    expect(servicio.disponible()).toBe(false);
    http.expectOne('/api/ia/estado').flush({ disponible: true });
    expect(servicio.disponible()).toBe(true);
  });

  it('si el estado falla, la IA queda como no disponible y no rompe nada', () => {
    http.expectOne('/api/ia/estado').flush(null, { status: 503, statusText: 'Service Unavailable' });
    expect(servicio.disponible()).toBe(false);
  });

  it('interpretar(): un 204 es "sin sugerencia", no un error', async () => {
    http.expectOne('/api/ia/estado').flush({ disponible: true });
    let resultado: unknown = 'sin emitir';

    servicio.interpretar('laptop para jugar barata').subscribe((r) => (resultado = r));
    await TestBed.inject(ApplicationRef).whenStable();
    const peticion = http.expectOne({ url: '/api/ia/busqueda/interpretar', method: 'POST' });
    expect(peticion.request.body).toEqual({ consulta: 'laptop para jugar barata' });
    peticion.flush(null, { status: 204, statusText: 'No Content' });

    expect(resultado).toBeNull();
  });

  it('las funciones públicas tragan sus errores: son mejoras, no dependencias', async () => {
    http.expectOne('/api/ia/estado').flush({ disponible: true });
    let resultado: unknown = 'sin emitir';

    servicio.resumenResenas(7).subscribe((r) => (resultado = r));
    await TestBed.inject(ApplicationRef).whenStable();
    http
      .expectOne('/api/ia/productos/7/resumen-resenas')
      .flush(null, { status: 500, statusText: 'Internal Server Error' });

    expect(resultado).toBeNull();
  });

  it('lo que se pide solo al abrir una pantalla no sale mientras la aplicación sigue cargando', async () => {
    const carga = http.expectOne('/api/ia/estado');

    servicio.complementos([1, 2]).subscribe();
    await Promise.resolve();
    // Con la petición del estado aún abierta, la aplicación no está estable.
    http.expectNone('/api/ia/carrito/complementos');

    carga.flush({ disponible: true });
    await TestBed.inject(ApplicationRef).whenStable();
    expect(http.expectOne('/api/ia/carrito/complementos').request.body).toEqual({ productoIds: [1, 2] });
  });
});
