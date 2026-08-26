import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CacheHttp } from '../cache/cache-http';
import { politicaPara, recursoDe } from '../cache/politica-cache';
import { SIN_CACHE, cacheInterceptor } from './cache.interceptor';

/*
 * Los ejemplos de aquí son ubigeo, métodos de pago y guías, y no el catálogo,
 * porque el catálogo ya no es de esta caché: lo lleva `CacheLecturaService` en
 * IndexedDB, que sobrevive a recargar la página y sirve sin conexión. Esta vive
 * en memoria y cubre lo que aquella no toca.
 */
const CACHEABLE = '/api/ubigeo/departamentos';
const OTRO_CACHEABLE = '/api/metodos-pago';

describe('cacheInterceptor', () => {
  let http: HttpClient;
  let servidor: HttpTestingController;
  let cache: CacheHttp;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([cacheInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    servidor = TestBed.inject(HttpTestingController);
    cache = TestBed.inject(CacheHttp);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('la segunda lectura sale de memoria y no toca la red', () => {
    http.get(CACHEABLE).subscribe();
    servidor.expectOne(CACHEABLE).flush(['Lima']);

    let recibido: unknown;
    http.get(CACHEABLE).subscribe((r) => (recibido = r));

    // Si hubiera salido a la red, `verify` fallaría por petición pendiente.
    servidor.verify();
    expect(recibido).toEqual(['Lima']);
  });

  it('lo que NO está en la lista blanca viaja siempre', () => {
    // El carrito, los pedidos y los pagos cambian con cada acción y además
    // dependen de quién pregunta: cachearlos sería enseñar datos de otro.
    for (const url of ['/api/carrito', '/api/pedidos/mios', '/api/envios/mios']) {
      http.get(url).subscribe();
      servidor.expectOne(url).flush([]);

      http.get(url).subscribe();
      servidor.expectOne(url).flush([]);
    }
  });

  it('una escritura invalida el recurso ENTERO, no solo la url escrita', () => {
    http.get('/api/guias').subscribe();
    servidor.expectOne('/api/guias').flush([{ id: 1 }]);

    http.get('/api/guias/como-elegir-monitor').subscribe();
    servidor.expectOne('/api/guias/como-elegir-monitor').flush({ id: 1 });

    /*
     * Publicar una guía se escribe en otra URL de la misma familia. Si solo se
     * invalidara la URL escrita, el índice y cada ficha seguirían enseñando lo
     * viejo, que es justo donde se nota.
     */
    http.post('/api/guias', {}).subscribe();
    servidor.expectOne({ url: '/api/guias', method: 'POST' }).flush({});

    http.get('/api/guias').subscribe();
    servidor.expectOne('/api/guias').flush([]);

    http.get('/api/guias/como-elegir-monitor').subscribe();
    servidor.expectOne('/api/guias/como-elegir-monitor').flush({ id: 1 });
  });

  it('una escritura en otro recurso no tira lo que no le toca', () => {
    http.get(CACHEABLE).subscribe();
    servidor.expectOne(CACHEABLE).flush([]);

    http.post('/api/carrito/items', {}).subscribe();
    servidor.expectOne('/api/carrito/items').flush({});

    http.get(CACHEABLE).subscribe();
    servidor.verify();
  });

  it('la entrada caduca cuando pasa su plazo', () => {
    vi.useFakeTimers();

    http.get(OTRO_CACHEABLE).subscribe();
    servidor.expectOne(OTRO_CACHEABLE).flush([]);

    // Los métodos de pago duran diez minutos: dentro del plazo no se repite.
    vi.advanceTimersByTime(5 * 60_000);
    http.get(OTRO_CACHEABLE).subscribe();
    servidor.verify();

    // Pasado el plazo, sí.
    vi.advanceTimersByTime(6 * 60_000);
    http.get(OTRO_CACHEABLE).subscribe();
    servidor.expectOne(OTRO_CACHEABLE).flush([]);
  });

  it('un error NO se cachea: si no, un fallo de red rompería la pantalla todo el plazo', () => {
    http.get(CACHEABLE).subscribe({ error: () => undefined });
    servidor.expectOne(CACHEABLE).flush('roto', { status: 500, statusText: 'Error' });

    // El reintento tiene que poder salir a la red.
    http.get(CACHEABLE).subscribe();
    servidor.expectOne(CACHEABLE).flush([]);
  });

  it('SIN_CACHE se salta lo guardado pero guarda lo que trae', () => {
    http.get(OTRO_CACHEABLE).subscribe();
    servidor.expectOne(OTRO_CACHEABLE).flush([{ id: 1 }]);

    const contexto = new HttpContext().set(SIN_CACHE, true);
    http.get(OTRO_CACHEABLE, { context: contexto }).subscribe();
    servidor.expectOne(OTRO_CACHEABLE).flush([{ id: 1 }, { id: 2 }]);

    // Y lo recién traído queda disponible para el resto de la pantalla.
    let recibido: unknown;
    http.get(OTRO_CACHEABLE).subscribe((r) => (recibido = r));
    servidor.verify();
    expect(recibido).toEqual([{ id: 1 }, { id: 2 }]);
  });

  it('cerrar sesión vacía la caché', () => {
    http.get(CACHEABLE).subscribe();
    servidor.expectOne(CACHEABLE).flush([]);
    expect(cache.tamano).toBe(1);

    cache.limpiar();

    http.get(CACHEABLE).subscribe();
    servidor.expectOne(CACHEABLE).flush([]);
  });
});

describe('política de caché', () => {
  it('solo entra lo que es igual para todo el mundo', () => {
    // Público y estable: lo ve igual cualquiera, con o sin sesión.
    expect(politicaPara('/api/ubigeo/departamentos')).toBeDefined();
    expect(politicaPara('/api/metodos-pago')).toBeDefined();
    expect(politicaPara('/api/guias')).toBeDefined();
    expect(politicaPara('/api/guias/como-elegir-monitor')).toBeDefined();

    // El panel de guías trae los borradores: queda fuera aunque sea de la
    // misma familia. Un patrón con comodín se lo habría llevado por delante.
    expect(politicaPara('/api/guias/admin')).toBeUndefined();
    expect(politicaPara('/api/guias/admin/todas')).toBeUndefined();

    // Y nada de lo que es de una persona.
    expect(politicaPara('/api/carrito')).toBeUndefined();
    expect(politicaPara('/api/pedidos/mios')).toBeUndefined();
    expect(politicaPara('/api/envios/mios')).toBeUndefined();
    expect(politicaPara('/api/pagos/preferencia')).toBeUndefined();
    expect(politicaPara('/api/usuarios/1')).toBeUndefined();
    expect(politicaPara('/api/auth/proveedores')).toBeUndefined();
  });

  it('el catálogo NO entra aquí: lo cachea IndexedDB', () => {
    /*
     * Es un reparto, no un olvido. `CacheLecturaService` guarda productos,
     * categorías, marcas y valoraciones en IndexedDB, que sobrevive a recargar
     * y sirve sin conexión; esta caché muere con la pestaña.
     *
     * Cachear en las dos capas no aportaría nada y daría dos caducidades
     * distintas y dos sitios que invalidar para el mismo dato, que es como se
     * acaba enseñando un precio viejo sin saber cuál de las dos lo guardó.
     */
    expect(politicaPara('/api/productos?page=0&size=12')).toBeUndefined();
    expect(politicaPara('/api/productos/portada')).toBeUndefined();
    expect(politicaPara('/api/productos/42')).toBeUndefined();
    expect(politicaPara('/api/productos/categoria/monitores?page=0')).toBeUndefined();
    expect(politicaPara('/api/categorias')).toBeUndefined();
    expect(politicaPara('/api/marcas')).toBeUndefined();
  });

  it('el recurso de una url es su primer segmento: es lo que invalida una escritura', () => {
    expect(recursoDe('/api/guias/como-elegir-monitor')).toBe('/api/guias');
    expect(recursoDe('/api/ubigeo/provincias?departamento=Lima')).toBe('/api/ubigeo');
    expect(recursoDe('/api/metodos-pago')).toBe('/api/metodos-pago');
  });
});
