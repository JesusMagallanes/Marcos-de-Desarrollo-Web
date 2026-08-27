import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { CarritoService } from './carrito.service';
import { AuthService } from '../../usuarios/services/auth.service';

describe('CarritoService', () => {
  let service: CarritoService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        CarritoService,
        {
          provide: AuthService,
          useValue: {
            autenticado: () => true,
          },
        },
      ],
    });
    service = TestBed.inject(CarritoService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('obtener() llama a GET /api/carrito', () => {
    const mock = {
      items: [{ id: 1, productoId: 10, nombre: 'Laptop', precio: 5000, cantidad: 1, imageUrl: null, stock: 5 }],
      subtotal: 5000,
      costoEnvio: 0,
      total: 5000,
    };

    service.obtener().subscribe((carrito) => {
      expect(carrito.items.length).toBe(1);
      expect(carrito.subtotal).toBe(5000);
    });

    const req = httpMock.expectOne('/api/carrito');
    expect(req.request.method).toBe('GET');
    req.flush(mock);
  });

  it('agregar() llama a POST /api/carrito/items', () => {
    const mock = { items: [], subtotal: 0, costoEnvio: 15, total: 15 };

    service.agregar(10, 2).subscribe();

    const req = httpMock.expectOne('/api/carrito/items');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ productoId: 10, cantidad: 2 });
    req.flush(mock);
  });

  it('cambiarCantidad() llama a PUT /api/carrito/items/{id}', () => {
    const mock = { items: [], subtotal: 0, costoEnvio: 15, total: 15 };

    service.cambiarCantidad(5, 3).subscribe();

    const req = httpMock.expectOne('/api/carrito/items/5');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ cantidad: 3 });
    req.flush(mock);
  });

  it('eliminar() llama a DELETE /api/carrito/items/{id}', () => {
    const mock = { items: [], subtotal: 0, costoEnvio: 15, total: 15 };

    service.eliminar(5).subscribe();

    const req = httpMock.expectOne('/api/carrito/items/5');
    expect(req.request.method).toBe('DELETE');
    req.flush(mock);
  });

  it('vaciar() llama a DELETE /api/carrito', () => {
    const mock = { items: [], subtotal: 0, costoEnvio: 0, total: 0 };

    service.vaciar().subscribe();

    const req = httpMock.expectOne('/api/carrito');
    expect(req.request.method).toBe('DELETE');
    req.flush(mock);
  });

  it('aplicar() actualiza los signals del carrito', () => {
    const mock = {
      items: [{ id: 1, productoId: 10, nombre: 'Laptop', precio: 5000, cantidad: 2, imageUrl: null, stock: 5 }],
      subtotal: 10000,
      costoEnvio: 0,
      total: 10000,
    };

    service.obtener().subscribe();

    const req = httpMock.expectOne('/api/carrito');
    req.flush(mock);

    expect(service.items().length).toBe(1);
    expect(service.subtotal()).toBe(10000);
    expect(service.costoEnvio()).toBe(0);
    expect(service.total()).toBe(10000);
    expect(service.cantidadTotal()).toBe(2);
    expect(service.vacio()).toBe(false);
  });

  it('vaciarLocal() resetea todos los signals', () => {
    // Primero cargar datos
    const mock = {
      items: [{ id: 1, productoId: 10, nombre: 'Laptop', precio: 5000, cantidad: 1, imageUrl: null, stock: 5 }],
      subtotal: 5000,
      costoEnvio: 0,
      total: 5000,
    };
    service.obtener().subscribe();
    httpMock.expectOne('/api/carrito').flush(mock);

    // Verificar que hay datos
    expect(service.items().length).toBe(1);

    // Ahora simular que no hay sesión
    const authService = TestBed.inject(AuthService) as { autenticado: () => boolean };
    authService.autenticado = () => false;
    service.refrescar();

    expect(service.items().length).toBe(0);
    expect(service.subtotal()).toBe(0);
    expect(service.vacio()).toBe(true);
  });
});
