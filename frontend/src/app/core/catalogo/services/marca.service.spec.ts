import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { MarcaService } from './marca.service';
import { CacheLecturaService } from '../../offline';

describe('MarcaService', () => {
  let service: MarcaService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        MarcaService,
        { provide: CacheLecturaService, useValue: { obtener: (_k: string, _t: number, fn: () => unknown) => fn(), invalidar: () => Promise.resolve() } },
      ],
    });
    service = TestBed.inject(MarcaService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listar() llama a GET /api/marcas', () => {
    service.listar().subscribe();
    httpMock.expectOne('/api/marcas').flush([]);
  });

  it('listarPorCategoria() llama a GET /api/marcas/categoria/{id}', () => {
    service.listarPorCategoria(1).subscribe();
    const req = httpMock.expectOne('/api/marcas/categoria/1');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('crear() llama a POST /api/marcas', () => {
    service.crear({ name: 'Asus', descripcion: 'Tecnología', categoriaId: 1 }).subscribe();
    const req = httpMock.expectOne('/api/marcas');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 1, name: 'Asus' });
  });

  it('actualizar() llama a PUT /api/marcas/{id}', () => {
    service.actualizar(1, { name: 'Asus Updated', descripcion: 'Actualizada', categoriaId: 1 }).subscribe();
    const req = httpMock.expectOne('/api/marcas/1');
    expect(req.request.method).toBe('PUT');
    req.flush({ id: 1, name: 'Asus Updated' });
  });

  it('eliminar() llama a DELETE /api/marcas/{id}', () => {
    service.eliminar(1).subscribe();
    const req = httpMock.expectOne('/api/marcas/1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
