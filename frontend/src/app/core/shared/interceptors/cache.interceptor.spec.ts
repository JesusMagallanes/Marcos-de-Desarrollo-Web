import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CacheHttp } from '../cache/cache-http';
import { politicaPara, recursoDe } from '../cache/politica-cache';
import { SIN_CACHE, cacheInterceptor } from './cache.interceptor';

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
    http.get('/api/categorias').subscribe();
    servidor.expectOne('/api/categorias').flush([{ id: 1 }]);

    let recibido: unknown;
    http.get('/api/categorias').subscribe((r) => (recibido = r));

    // Si hubiera salido a la red, `verify` fallaría por petición pendiente.
    servidor.verify();
    expect(recibido).toEqual([{ id: 1 }]);
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
    http.get('/api/productos').subscribe();
    servidor.expectOne('/api/productos').flush([{ id: 1 }]);

    http.get('/api/productos/7').subscribe();
    servidor.expectOne('/api/productos/7').flush({ id: 7 });

    /*
     * Un descuento se aplica en otra URL de la misma familia. Si solo se
     * invalidara la URL escrita, la lista y la ficha seguirían enseñando el
     * precio viejo, que es exactamente donde se nota.
     */
    http.post('/api/productos/descuento', {}).subscribe();
    servidor.expectOne('/api/productos/descuento').flush([]);

    http.get('/api/productos').subscribe();
    servidor.expectOne('/api/productos').flush([]);

    http.get('/api/productos/7').subscribe();
    servidor.expectOne('/api/productos/7').flush({ id: 7 });
  });

  it('una escritura en otro recurso no tira la caché del catálogo', () => {
    http.get('/api/categorias').subscribe();
    servidor.expectOne('/api/categorias').flush([]);

    http.post('/api/carrito/items', {}).subscribe();
    servidor.expectOne('/api/carrito/items').flush({});

    http.get('/api/categorias').subscribe();
    servidor.verify();
  });

  it('la entrada caduca cuando pasa su plazo', () => {
    vi.useFakeTimers();

    http.get('/api/productos').subscribe();
    servidor.expectOne('/api/productos').flush([]);

    // Los productos duran un minuto: dentro del plazo no se repite.
    vi.advanceTimersByTime(30_000);
    http.get('/api/productos').subscribe();
    servidor.verify();

    // Pasado el plazo, sí.
    vi.advanceTimersByTime(31_000);
    http.get('/api/productos').subscribe();
    servidor.expectOne('/api/productos').flush([]);
  });

  it('un error NO se cachea: si no, un fallo de red rompería la pantalla todo el plazo', () => {
    http.get('/api/categorias').subscribe({ error: () => undefined });
    servidor.expectOne('/api/categorias').flush('roto', { status: 500, statusText: 'Error' });

    // El reintento tiene que poder salir a la red.
    http.get('/api/categorias').subscribe();
    servidor.expectOne('/api/categorias').flush([]);
  });

  it('SIN_CACHE se salta lo guardado pero guarda lo que trae', () => {
    http.get('/api/productos').subscribe();
    servidor.expectOne('/api/productos').flush([{ id: 1 }]);

    const contexto = new HttpContext().set(SIN_CACHE, true);
    http.get('/api/productos', { context: contexto }).subscribe();
    servidor.expectOne('/api/productos').flush([{ id: 1 }, { id: 2 }]);

    // Y lo recién traído queda disponible para el resto de la pantalla.
    let recibido: unknown;
    http.get('/api/productos').subscribe((r) => (recibido = r));
    servidor.verify();
    expect(recibido).toEqual([{ id: 1 }, { id: 2 }]);
  });

  it('cerrar sesión vacía la caché', () => {
    http.get('/api/categorias').subscribe();
    servidor.expectOne('/api/categorias').flush([]);
    expect(cache.tamano).toBe(1);

    cache.limpiar();

    http.get('/api/categorias').subscribe();
    servidor.expectOne('/api/categorias').flush([]);
  });
});

describe('política de caché', () => {
  it('solo entra lo que es igual para todo el mundo', () => {
    // Público: lo ve igual cualquiera, con o sin sesión.
    expect(politicaPara('/api/productos')).toBeDefined();
    expect(politicaPara('/api/productos/42')).toBeDefined();
    expect(politicaPara('/api/productos/categoria/monitores?page=0')).toBeDefined();
    expect(politicaPara('/api/categorias')).toBeDefined();
    expect(politicaPara('/api/ubigeo/departamentos')).toBeDefined();

    /*
     * Estas dos son de la misma familia que los productos y NO se cachean: son
     * las que enseñan cosas distintas según quién pregunte. Un patrón con
     * comodín se las habría llevado por delante, y el colaborador vería en su
     * lista los productos pendientes de otro.
     */
    expect(politicaPara('/api/productos/mios')).toBeUndefined();
    expect(politicaPara('/api/productos/moderacion')).toBeUndefined();

    // Y lo mismo con las guías: el panel trae los borradores.
    expect(politicaPara('/api/guias')).toBeDefined();
    expect(politicaPara('/api/guias/como-elegir-monitor')).toBeDefined();
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

  it('cubre las URLs que la aplicación produce HOY, con paginación y todo', () => {
    /*
     * La política se escribió antes de paginar el catálogo, y paginar cambió
     * todas esas URLs. Esta prueba las fija tal y como salen ahora: si mañana
     * alguien cambia un parámetro o mueve un endpoint, se entera aquí y no
     * porque la tienda empiece a ir lenta sin motivo aparente.
     */
    expect(politicaPara('/api/productos?page=0&size=12')).toBeDefined();
    expect(politicaPara('/api/productos?page=0&size=6&search=lg')).toBeDefined();
    expect(politicaPara('/api/productos/portada')).toBeDefined();
    expect(politicaPara('/api/productos/categoria/monitores?page=0&size=12')).toBeDefined();

    /*
     * El panel de descuentos NO se cachea, y es deliberado por partida doble:
     * ni entra en la lista blanca, ni lo pediría de caché el servicio, que lo
     * marca `SIN_CACHE`. Es una pantalla de edición: quien acaba de aplicar un
     * descuento tiene que ver el resultado, no lo de hace medio minuto.
     */
    expect(politicaPara('/api/productos/descuentos?estado=activo&page=0&size=25')).toBeUndefined();
  });

  it('aplicar un descuento invalida también la portada', () => {
    // La portada enseña el carrusel de ofertas: si sobreviviera a un descuento,
    // seguiría anunciando los precios viejos hasta que caducara su minuto.
    expect(recursoDe('/api/productos/portada')).toBe(recursoDe('/api/productos/descuento'));
  });

  it('el recurso de una url es su primer segmento: es lo que invalida una escritura', () => {
    expect(recursoDe('/api/productos/7')).toBe('/api/productos');
    expect(recursoDe('/api/productos/descuento')).toBe('/api/productos');
    expect(recursoDe('/api/productos?search=lg')).toBe('/api/productos');
    expect(recursoDe('/api/categorias')).toBe('/api/categorias');
  });
});
