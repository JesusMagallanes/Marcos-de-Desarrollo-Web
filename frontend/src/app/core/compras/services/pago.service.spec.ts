import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { PagoService } from './pago.service';

describe('PagoService', () => {
  let service: PagoService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), PagoService],
    });
    service = TestBed.inject(PagoService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('crearPreferencia() llama a POST /api/pagos/preferencia', () => {
    const entrega = {
      direccion: 'Av Lima 123',
      calle: 'Av Lima',
      numero: '123',
      referencia: 'Frente al parque',
      codigoPostal: '15001',
      distrito: 'Miraflores',
      provincia: 'Lima',
      departamento: 'Lima',
      receptorNombre: 'Juan',
      telefonoContacto: '999999999',
    };

    service.crearPreferencia(1, entrega).subscribe();

    const req = httpMock.expectOne('/api/pagos/preferencia');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.metodoPagoId).toBe(1);
    expect(req.request.body.entrega.calle).toBe('Av Lima');
    req.flush({ preferenceId: 'mp-pref-123', initPoint: 'https://mp.test' });
  });

  it('crearPreferencia() trimea los campos de entrega', () => {
    const entrega = {
      direccion: '  Av Lima 123  ',
      calle: '  Av Lima  ',
      numero: '  123  ',
      referencia: '  ',
      codigoPostal: '  15001  ',
      distrito: '  Miraflores  ',
      provincia: '  Lima  ',
      departamento: '  Lima  ',
      receptorNombre: '  Juan  ',
      telefonoContacto: '  999999999  ',
    };

    service.crearPreferencia(1, entrega).subscribe();

    const req = httpMock.expectOne('/api/pagos/preferencia');
    expect(req.request.body.entrega.calle).toBe('Av Lima');
    expect(req.request.body.entrega.numero).toBe('123');
    expect(req.request.body.entrega.referencia).toBeUndefined();
    req.flush({ preferenceId: 'mp-pref-123', initPoint: 'https://mp.test' });
  });

  it('confirmar() llama a POST /api/pagos/confirmar', () => {
    service.confirmar('mp-payment-123').subscribe();
    const req = httpMock.expectOne('/api/pagos/confirmar');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ paymentId: 'mp-payment-123' });
    req.flush({ id: 1, estado: 'PAGADO' });
  });

  it('verificar() llama a POST /api/pagos/verificar', () => {
    service.verificar().subscribe();
    const req = httpMock.expectOne('/api/pagos/verificar');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush({ estado: 'SIN_PAGO' });
  });
});
