import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { bd } from '../../offline';
import { esperarPeticion } from '../../shared/testing/esperar-peticion';
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

/** La URL de la primera página con el tamaño por defecto. */
const PRIMERA_PAGINA = '/api/productos?page=0&size=12';

describe('ProductoService', () => {
  let servicio: ProductoService;
  let http: HttpTestingController;

  beforeEach(async () => {
    // La caché del catálogo vive en IndexedDB: cada prueba empieza con las
    // tablas vacías para que las peticiones HTTP sean predecibles.
    await Promise.all([bd.cache.clear(), bd.cola.clear()]);
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(ProductoService);
    http = TestBed.inject(HttpTestingController);
  });

  /* ══════════════ Paginación ══════════════ */

  it('listar() pide una PÁGINA, no el catálogo entero', async () => {
    servicio.listar().subscribe();

    // Devolvía un array con todos los productos aprobados, sin tope, en el
    // endpoint más visitado de la tienda.
    const req = await esperarPeticion(http, PRIMERA_PAGINA);
    expect(req.request.method).toBe('GET');
    req.flush(paginaCon(producto(1)));
  });

  it('listar() acota el tamaño de página al límite del backend', async () => {
    // El servidor rechaza size > 100 con un 400; recortarlo aquí ahorra el viaje.
    servicio.listar(null, 0, 5000).subscribe();

    (await esperarPeticion(http, '/api/productos?page=0&size=100')).flush(paginaCon());
  });

  it('listarPorCategoria() envía la paginación', async () => {
    servicio.listarPorCategoria('monitores', 2, 24).subscribe();

    const req = await esperarPeticion(http, '/api/productos/categoria/monitores?page=2&size=24');
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], number: 2, size: 24, totalElements: 0, totalPages: 0 });
  });

  it('portada() trae las tres listas en un solo viaje', async () => {
    servicio.portada().subscribe();

    const req = await esperarPeticion(http, '/api/productos/portada');
    expect(req.request.method).toBe('GET');
    req.flush({ destacados: [], ofertas: [], porCategoria: [] });
  });

  /* ══════════════ Caché en IndexedDB ══════════════ */

  it('cachea la página: la segunda llamada no repite la petición', async () => {
    servicio.listar().subscribe();
    (await esperarPeticion(http, PRIMERA_PAGINA)).flush(paginaCon(producto(1)));

    // El guardado en IndexedDB es "dispara y olvida": esperamos a que aterrice
    // para que la segunda lectura lo encuentre.
    await vi.waitFor(async () => {
      expect(await bd.cache.get('productos:pagina:0:12')).toBeDefined();
    });

    servicio.listar().subscribe();

    // Si no hubiera caché, esto fallaría por petición inesperada.
    http.verify();
  });

  it('cada página es una entrada distinta', async () => {
    servicio.listar(null, 0).subscribe();
    (await esperarPeticion(http, PRIMERA_PAGINA)).flush(paginaCon(producto(1)));

    servicio.listar(null, 1).subscribe();
    (await esperarPeticion(http, '/api/productos?page=1&size=12')).flush(paginaCon(producto(2)));
  });

  it('la búsqueda NO se cachea, porque los términos son infinitos', async () => {
    // Cachearlos llenaría IndexedDB de entradas de un solo uso.
    servicio.listar('lg').subscribe();
    http.expectOne('/api/productos?page=0&size=12&search=lg').flush(paginaCon());

    servicio.listar('lg').subscribe();
    http.expectOne('/api/productos?page=0&size=12&search=lg').flush(paginaCon());
  });

  /* ══════════════ Invalidación ══════════════ */

  it('crear() invalida la caché para que el listado refleje el alta', async () => {
    servicio.listar().subscribe();
    (await esperarPeticion(http, PRIMERA_PAGINA)).flush(paginaCon(producto(1)));

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
    (await esperarPeticion(http, PRIMERA_PAGINA)).flush(paginaCon(producto(1), producto(2)));
  });

  it('eliminar() también invalida la caché', async () => {
    servicio.listar().subscribe();
    (await esperarPeticion(http, PRIMERA_PAGINA)).flush(paginaCon(producto(1)));

    servicio.eliminar(1).subscribe();
    http.expectOne({ url: '/api/productos/1', method: 'DELETE' }).flush(null);

    servicio.listar().subscribe();
    (await esperarPeticion(http, PRIMERA_PAGINA)).flush(paginaCon());
  });

  it('recargar() fuerza una lectura fresca', async () => {
    servicio.listar().subscribe();
    (await esperarPeticion(http, PRIMERA_PAGINA)).flush(paginaCon(producto(1)));

    servicio.recargar().subscribe();
    (await esperarPeticion(http, PRIMERA_PAGINA)).flush(paginaCon(producto(1), producto(2)));
  });

  it('aplicarDescuento() pega a /api/productos/descuento e invalida la caché', async () => {
    servicio.listar().subscribe();
    (await esperarPeticion(http, PRIMERA_PAGINA)).flush(paginaCon(producto(1)));

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
    (await esperarPeticion(http, PRIMERA_PAGINA)).flush(paginaCon());
  });

  it('quitarDescuento() pega a /api/productos/descuento/limpiar e invalida la caché', async () => {
    servicio.listar().subscribe();
    (await esperarPeticion(http, PRIMERA_PAGINA)).flush(paginaCon(producto(1)));

    servicio.quitarDescuento({ productoIds: [1] }).subscribe();
    const req = http.expectOne({ url: '/api/productos/descuento/limpiar', method: 'POST' });
    expect(req.request.body).toEqual({ productoIds: [1] });
    req.flush([producto(1)]);

    servicio.listar().subscribe();
    (await esperarPeticion(http, PRIMERA_PAGINA)).flush(paginaCon());
  });

  /* ══════════════ El panel de descuentos ══════════════ */

  it('paraDescuentos() NO se cachea: es una pantalla de edición', () => {
    const filtros = { estado: 'activo' as const, categoriaId: null, marcaId: null, texto: null };

    servicio.paraDescuentos(filtros).subscribe();
    http.expectOne('/api/productos/descuentos?estado=activo&page=0&size=25').flush({
      content: [],
      number: 0,
      size: 25,
      totalElements: 0,
      totalPages: 0,
      conteos: { todos: 0, activo: 0, programado: 0, inactivo: 0 },
    });

    // Quien acaba de aplicar un descuento tiene que ver el resultado, no lo
    // que se leyó hace un rato.
    servicio.paraDescuentos(filtros).subscribe();
    http.expectOne('/api/productos/descuentos?estado=activo&page=0&size=25').flush({
      content: [],
      number: 0,
      size: 25,
      totalElements: 0,
      totalPages: 0,
      conteos: { todos: 0, activo: 0, programado: 0, inactivo: 0 },
    });
  });
});
