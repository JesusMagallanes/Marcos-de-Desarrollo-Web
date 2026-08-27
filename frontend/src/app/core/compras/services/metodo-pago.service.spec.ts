import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { MetodoPagoService } from './metodo-pago.service';

describe('MetodoPagoService', () => {
  let service: MetodoPagoService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), MetodoPagoService],
    });
    service = TestBed.inject(MetodoPagoService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listar() llama a GET /api/metodos-pago', () => {
    service.listar().subscribe();
    const req = httpMock.expectOne('/api/metodos-pago');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('crear() llama a POST /api/metodos-pago', () => {
    // El tipo viaja en el cuerpo: es lo que decide si el checkout va a la
    // pasarela o cierra el pedido para cobrarlo al entregarlo.
    const dto = { name: 'MercadoPago', description: 'Pago online', tipo: 'MERCADOPAGO' as const };
    service.crear(dto).subscribe();
    const req = httpMock.expectOne('/api/metodos-pago');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(dto);
    req.flush({ id: 1, ...dto });
  });

  it('actualizar() llama a PUT /api/metodos-pago/{id}', () => {
    const dto = { name: 'MercadoPago', description: 'Actualizado', tipo: 'MERCADOPAGO' as const };
    service.actualizar(1, dto).subscribe();
    const req = httpMock.expectOne('/api/metodos-pago/1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(dto);
    req.flush({ id: 1, ...dto });
  });

  it('eliminar() llama a DELETE /api/metodos-pago/{id}', () => {
    service.eliminar(1).subscribe();
    const req = httpMock.expectOne('/api/metodos-pago/1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
