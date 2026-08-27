import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { GuiaService } from './guia.service';

describe('GuiaService', () => {
  let service: GuiaService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), GuiaService],
    });
    service = TestBed.inject(GuiaService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listar() llama a GET /api/guias', () => {
    service.listar().subscribe();
    const req = httpMock.expectOne('/api/guias');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('obtener() llama a GET /api/guias/{slug}', () => {
    service.obtener('como-comprar').subscribe();
    const req = httpMock.expectOne('/api/guias/como-comprar');
    expect(req.request.method).toBe('GET');
    req.flush({ id: 1, slug: 'como-comprar', titulo: 'Cómo comprar' });
  });

  it('listarTodas() llama a GET /api/guias/admin/todas', () => {
    service.listarTodas().subscribe();
    const req = httpMock.expectOne('/api/guias/admin/todas');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('obtenerParaEdicion() llama a GET /api/guias/admin/{slug}', () => {
    service.obtenerParaEdicion('como-comprar').subscribe();
    const req = httpMock.expectOne('/api/guias/admin/como-comprar');
    expect(req.request.method).toBe('GET');
    req.flush({ id: 1, slug: 'como-comprar' });
  });

  it('crear() llama a POST /api/guias', () => {
    const dto = { slug: 'nueva', titulo: 'Nueva', resumen: 'Resumen', icono: 'star', posicion: 1, publicada: true, pasos: [] };
    service.crear(dto).subscribe();
    const req = httpMock.expectOne('/api/guias');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 3, ...dto });
  });

  it('eliminar() llama a DELETE /api/guias/{id}', () => {
    service.eliminar(1).subscribe();
    const req = httpMock.expectOne('/api/guias/1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
