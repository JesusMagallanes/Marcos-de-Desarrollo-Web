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
    precio: 999.9,
    imageUrl: null,
    stock: 5,
    categoriaId: 1,
    categoriaName: 'Monitores',
    marcaId: 1,
    marcaName: 'LG',
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
        precio: 10,
        imageUrl: null,
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
});
