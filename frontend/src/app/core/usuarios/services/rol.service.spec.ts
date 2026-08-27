import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { RolService } from './rol.service';

describe('RolService', () => {
  let service: RolService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), RolService],
    });
    service = TestBed.inject(RolService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listar() llama a GET /api/roles', () => {
    service.listar().subscribe();
    httpMock.expectOne('/api/roles').flush([]);
  });

  it('catalogoPermisos() llama a GET /api/roles/permisos', () => {
    service.catalogoPermisos().subscribe();
    httpMock.expectOne('/api/roles/permisos').flush([]);
  });

  it('crear() llama a POST /api/roles', () => {
    service.crear({ nombre: 'TEST', descripcion: 'Test', tipo: 'CLIENTE', permisos: [] }).subscribe();
    const req = httpMock.expectOne('/api/roles');
    expect(req.request.method).toBe('POST');
    req.flush({ nombre: 'TEST' });
  });

  it('actualizar() llama a PUT /api/roles/{nombre}', () => {
    service.actualizar('TEST', { descripcion: 'Updated', tipo: 'CLIENTE', permisos: [] }).subscribe();
    const req = httpMock.expectOne('/api/roles/TEST');
    expect(req.request.method).toBe('PUT');
    req.flush({ nombre: 'TEST' });
  });

  it('eliminar() llama a DELETE /api/roles/{nombre}', () => {
    service.eliminar('TEST').subscribe();
    httpMock.expectOne('/api/roles/TEST').flush(null);
  });
});
