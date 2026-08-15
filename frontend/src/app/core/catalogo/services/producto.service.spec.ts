import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { Producto } from '../models';
import { ProductoService } from './producto.service';

function producto(id: number, nombre = 'Monitor'): Producto {
  return {
    id,
    name: nombre,
    description: 'desc',
    specifications: null,
    precio: 999.9,
    precioOferta: null,
    descuentoTipo: null,
    descuentoValor: null,
    ofertaInicio: null,
    ofertaFin: null,
    precioActual: 999.9,
    enOferta: false,
    calificacionPromedio: null,
    cantidadValoraciones: null,
    imageUrl: null,
    imagenes: [],
    stock: 5,
    categoriaId: 1,
    categoriaName: 'Monitores',
    marcaId: 1,
    marcaName: 'LG',
    // Un producto de la tienda: sin dueño y ya publicado.
    propietarioId: null,
    estadoModeracion: 'APROBADO',
    motivoRechazo: null,
  };
}

describe('ProductoService', () => {
  let servicio: ProductoService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(ProductoService);
    http = TestBed.inject(HttpTestingController);
  });

  it('listar() pega a /api/productos', () => {
    servicio.listar().subscribe();

    const req = http.expectOne('/api/productos');
    expect(req.request.method).toBe('GET');
    req.flush([producto(1)]);
  });

  it('cachea el catálogo completo: la segunda llamada no repite la petición', () => {
    servicio.listar().subscribe();
    http.expectOne('/api/productos').flush([producto(1)]);

    servicio.listar().subscribe();

    // Si no hubiera caché, esto fallaría por petición inesperada.
    http.verify();
  });

  it('la búsqueda NO se cachea, porque varía con el término', () => {
    servicio.listar('lg').subscribe();
    http.expectOne('/api/productos?search=lg').flush([]);

    servicio.listar('asus').subscribe();
    http.expectOne('/api/productos?search=asus').flush([]);
  });

  it('crear() invalida la caché para que el listado refleje el alta', () => {
    servicio.listar().subscribe();
    http.expectOne('/api/productos').flush([producto(1)]);

    servicio
      .crear({
        name: 'Nuevo',
        description: 'd',
        specifications: null,
        precio: 10,
        imagenes: ['https://img/a.png'],
        stock: 1,
        categoriaId: 1,
        marcaId: 1,
      })
      .subscribe();
    http.expectOne({ url: '/api/productos', method: 'POST' }).flush(producto(2, 'Nuevo'));

    servicio.listar().subscribe();

    // Tras la escritura la caché quedó descartada: vuelve a pedir.
    http.expectOne({ url: '/api/productos', method: 'GET' }).flush([producto(1), producto(2)]);
  });

  it('eliminar() también invalida la caché', () => {
    servicio.listar().subscribe();
    http.expectOne('/api/productos').flush([producto(1)]);

    servicio.eliminar(1).subscribe();
    http.expectOne({ url: '/api/productos/1', method: 'DELETE' }).flush(null);

    servicio.listar().subscribe();
    http.expectOne({ url: '/api/productos', method: 'GET' }).flush([]);
  });

  it('listarPorCategoria() envía la paginación', () => {
    servicio.listarPorCategoria('monitores', 2, 24).subscribe();

    const req = http.expectOne('/api/productos/categoria/monitores?page=2&size=24');
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], number: 2, size: 24, totalElements: 0, totalPages: 0 });
  });

  it('recargar() fuerza una lectura fresca', () => {
    servicio.listar().subscribe();
    http.expectOne('/api/productos').flush([producto(1)]);

    servicio.recargar().subscribe();
    http.expectOne('/api/productos').flush([producto(1), producto(2)]);
  });

  it('aplicarDescuento() pega a /api/productos/descuento e invalida la caché', () => {
    servicio.listar().subscribe();
    http.expectOne('/api/productos').flush([producto(1)]);

    servicio
      .aplicarDescuento({
        productoIds: [1],
        tipo: 'PORCENTAJE',
        valor: 15,
        inicio: '2026-08-11T00:00',
        fin: '2026-09-11T23:59',
      })
      .subscribe();
    const req = http.expectOne({ url: '/api/productos/descuento', method: 'POST' });
    expect(req.request.body).toMatchObject({ productoIds: [1], tipo: 'PORCENTAJE', valor: 15 });
    req.flush([producto(1)]);

    servicio.listar().subscribe();
    http.expectOne({ url: '/api/productos', method: 'GET' }).flush([]);
  });

  it('quitarDescuento() pega a /api/productos/descuento/limpiar e invalida la caché', () => {
    servicio.listar().subscribe();
    http.expectOne('/api/productos').flush([producto(1)]);

    servicio.quitarDescuento({ productoIds: [1] }).subscribe();
    const req = http.expectOne({ url: '/api/productos/descuento/limpiar', method: 'POST' });
    expect(req.request.body).toEqual({ productoIds: [1] });
    req.flush([producto(1)]);

    servicio.listar().subscribe();
    http.expectOne({ url: '/api/productos', method: 'GET' }).flush([]);
  });
});
