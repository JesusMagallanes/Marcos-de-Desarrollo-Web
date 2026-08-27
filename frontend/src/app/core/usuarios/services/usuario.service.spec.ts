import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { UsuarioService } from './usuario.service';

describe('UsuarioService', () => {
  let service: UsuarioService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), UsuarioService],
    });
    service = TestBed.inject(UsuarioService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listar() llama a GET /api/usuarios', () => {
    service.listar().subscribe();
    httpMock.expectOne('/api/usuarios').flush([]);
  });

  it('obtener() llama a GET /api/usuarios/{id}', () => {
    service.obtener(1).subscribe();
    httpMock.expectOne('/api/usuarios/1').flush({ id: 1 });
  });

  it('crear() llama a POST /api/usuarios', () => {
    const dto = { name: 'Test', lastname: 'User', emailAddress: 't@t.com', password: 'Pass123!', phoneNumber: '999', address: 'Av 1', rol: 'CLIENTE' };
    service.crear(dto).subscribe();
    const req = httpMock.expectOne('/api/usuarios');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 1, ...dto });
  });

  it('actualizarPerfil() llama a PUT /api/usuarios/{id}/perfil', () => {
    service.actualizarPerfil(1, { name: 'Nuevo', lastname: 'Nombre', emailAddress: 'n@n.com', phoneNumber: '999' }).subscribe();
    const req = httpMock.expectOne('/api/usuarios/1/perfil');
    expect(req.request.method).toBe('PUT');
    req.flush({ id: 1 });
  });

  it('cambiarRol() llama a PATCH /api/usuarios/{id}/rol', () => {
    service.cambiarRol(1, 'EMPLEADO').subscribe();
    const req = httpMock.expectOne('/api/usuarios/1/rol');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ rol: 'EMPLEADO' });
    req.flush({ id: 1, rol: 'EMPLEADO' });
  });

  it('eliminar() llama a DELETE /api/usuarios/{id}', () => {
    service.eliminar(1).subscribe();
    httpMock.expectOne('/api/usuarios/1').flush(null);
  });
});
