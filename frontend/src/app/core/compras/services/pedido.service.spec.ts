import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { PedidoService } from './pedido.service';

describe('PedidoService', () => {
  let service: PedidoService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), PedidoService],
    });
    service = TestBed.inject(PedidoService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('mios() llama a GET /api/pedidos/mios', () => {
    service.mios().subscribe();
    const req = httpMock.expectOne('/api/pedidos/mios');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('listar() sin estado llama a GET /api/pedidos', () => {
    service.listar().subscribe();
    const req = httpMock.expectOne('/api/pedidos');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys().length).toBe(0);
    req.flush([]);
  });

  it('listar() con estado agrega query param', () => {
    service.listar('PAGADO').subscribe();
    const req = httpMock.expectOne('/api/pedidos?estado=PAGADO');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('estado')).toBe('PAGADO');
    req.flush([]);
  });

  it('obtener() llama a GET /api/pedidos/{id}', () => {
    service.obtener(5).subscribe();
    const req = httpMock.expectOne('/api/pedidos/5');
    expect(req.request.method).toBe('GET');
    req.flush({ id: 5 });
  });

  it('cambiarEstado() llama a PATCH /api/pedidos/{id}/estado', () => {
    service.cambiarEstado(5, 'EN_TRANSITO').subscribe();
    const req = httpMock.expectOne('/api/pedidos/5/estado');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ estado: 'EN_TRANSITO' });
    req.flush({ id: 5, estado: 'EN_TRANSITO' });
  });

  it('eliminar() llama a DELETE /api/pedidos/{id}', () => {
    service.eliminar(5).subscribe();
    const req = httpMock.expectOne('/api/pedidos/5');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
