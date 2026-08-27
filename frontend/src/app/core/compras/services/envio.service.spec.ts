import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { EnvioService } from './envio.service';

describe('EnvioService', () => {
  let service: EnvioService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), EnvioService],
    });
    service = TestBed.inject(EnvioService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listar() sin estado llama a GET /api/envios', () => {
    service.listar().subscribe();
    const req = httpMock.expectOne('/api/envios');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys().length).toBe(0);
    req.flush([]);
  });

  it('listar() con estado agrega query param', () => {
    service.listar('PENDIENTE').subscribe();
    const req = httpMock.expectOne('/api/envios?estado=PENDIENTE');
    expect(req.request.params.get('estado')).toBe('PENDIENTE');
    req.flush([]);
  });

  it('mios() llama a GET /api/envios/mios', () => {
    service.mios().subscribe();
    const req = httpMock.expectOne('/api/envios/mios');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('cambiarEstado() llama a PATCH /api/envios/{id}/estado', () => {
    service.cambiarEstado(5, 'EN_TRANSITO').subscribe();
    const req = httpMock.expectOne('/api/envios/5/estado');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ estadoEnvio: 'EN_TRANSITO' });
    req.flush({ id: 5, estadoEnvio: 'EN_TRANSITO' });
  });
});
