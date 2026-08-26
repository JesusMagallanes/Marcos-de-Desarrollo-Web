import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { cacheInterceptor } from '../../shared/interceptors';
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

/** Una página con los productos que se le pasen; el resto de campos, coherentes. */
function paginaCon(...items: Producto[]) {
  return { content: items, number: 0, size: 12, totalElements: items.length, totalPages: 1 };
}

describe('ProductoService', () => {
  let servicio: ProductoService;
  let http: HttpTestingController;

  beforeEach(() => {
    /*
     * Con `cacheInterceptor`, que es quien cachea desde que se quitaron las
     * cachés a mano de los servicios. Las pruebas de más abajo comprueban el
     * comportamiento que ve quien usa el servicio —la segunda lectura no repite
     * la petición, una escritura la invalida— y ese comportamiento sigue siendo
     * el mismo; lo que cambió es dónde vive.
     */
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([cacheInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    servicio = TestBed.inject(ProductoService);
    http = TestBed.inject(HttpTestingController);
  });

  it('listar() pide una PÁGINA, no el catálogo entero', () => {
    servicio.listar().subscribe();

    // Devolvía un array con todos los productos aprobados, sin tope, en el
    // endpoint más visitado de la tienda.
    const req = http.expectOne('/api/productos?page=0&size=12');
    expect(req.request.method).toBe('GET');
    req.flush(paginaCon(producto(1)));
  });

  it('listar() acota el tamaño de página al límite del backend', () => {
    // El servidor rechaza size > 100 con un 400; recortarlo aquí ahorra el viaje.
    servicio.listar(null, 0, 5000).subscribe();

    http.expectOne('/api/productos?page=0&size=100').flush(paginaCon());
  });

  it('portada() trae las tres listas en un solo viaje', () => {
    servicio.portada().subscribe();

    const req = http.expectOne('/api/productos/portada');
    expect(req.request.method).toBe('GET');
    req.flush({ destacados: [], ofertas: [], porCategoria: [] });
  });

  it('cachea la página: la segunda llamada no repite la petición', () => {
    servicio.listar().subscribe();
    http.expectOne('/api/productos?page=0&size=12').flush(paginaCon(producto(1)));

    servicio.listar().subscribe();

    // Si no hubiera caché, esto fallaría por petición inesperada.
    http.verify();
  });

  it('cada página es una entrada de caché distinta', () => {
    servicio.listar(null, 0).subscribe();
    http.expectOne('/api/productos?page=0&size=12').flush(paginaCon(producto(1)));

    servicio.listar(null, 1).subscribe();
    http.expectOne('/api/productos?page=1&size=12').flush(paginaCon(producto(2)));
  });

  it('cada búsqueda es una entrada distinta: el término va en la clave', () => {
    servicio.listar('lg').subscribe();
    http.expectOne('/api/productos?page=0&size=12&search=lg').flush(paginaCon());

    servicio.listar('asus').subscribe();
    http.expectOne('/api/productos?page=0&size=12&search=asus').flush(paginaCon());

    // Y repetir una búsqueda ya hecha no vuelve a salir a la red.
    servicio.listar('lg').subscribe();
    http.verify();
  });

  it('crear() invalida la caché para que el listado refleje el alta', () => {
    servicio.listar().subscribe();
    http.expectOne('/api/productos?page=0&size=12').flush(paginaCon(producto(1)));

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
    http.expectOne({ url: '/api/productos?page=0&size=12', method: 'GET' })
      .flush(paginaCon(producto(1), producto(2)));
  });

  it('eliminar() también invalida la caché', () => {
    servicio.listar().subscribe();
    http.expectOne('/api/productos?page=0&size=12').flush(paginaCon(producto(1)));

    servicio.eliminar(1).subscribe();
    http.expectOne({ url: '/api/productos/1', method: 'DELETE' }).flush(null);

    servicio.listar().subscribe();
    http.expectOne({ url: '/api/productos?page=0&size=12', method: 'GET' }).flush(paginaCon());
  });

  it('listarPorCategoria() envía la paginación', () => {
    servicio.listarPorCategoria('monitores', 2, 24).subscribe();

    const req = http.expectOne('/api/productos/categoria/monitores?page=2&size=24');
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], number: 2, size: 24, totalElements: 0, totalPages: 0 });
  });

  it('recargar() fuerza una lectura fresca', () => {
    servicio.listar().subscribe();
    http.expectOne('/api/productos?page=0&size=12').flush(paginaCon(producto(1)));

    servicio.recargar().subscribe();
    http.expectOne('/api/productos?page=0&size=12').flush(paginaCon(producto(1), producto(2)));
  });

  it('aplicarDescuento() pega a /api/productos/descuento e invalida la caché', () => {
    servicio.listar().subscribe();
    http.expectOne('/api/productos?page=0&size=12').flush(paginaCon(producto(1)));

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
    http.expectOne({ url: '/api/productos?page=0&size=12', method: 'GET' }).flush(paginaCon());
  });

  it('quitarDescuento() pega a /api/productos/descuento/limpiar e invalida la caché', () => {
    servicio.listar().subscribe();
    http.expectOne('/api/productos?page=0&size=12').flush(paginaCon(producto(1)));

    servicio.quitarDescuento({ productoIds: [1] }).subscribe();
    const req = http.expectOne({ url: '/api/productos/descuento/limpiar', method: 'POST' });
    expect(req.request.body).toEqual({ productoIds: [1] });
    req.flush([producto(1)]);

    servicio.listar().subscribe();
    http.expectOne({ url: '/api/productos?page=0&size=12', method: 'GET' }).flush(paginaCon());
  });
});
