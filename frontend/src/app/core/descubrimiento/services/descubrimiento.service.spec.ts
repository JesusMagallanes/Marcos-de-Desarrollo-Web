import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DescubrimientoService } from './descubrimiento.service';

const EVENTOS = '/api/descubrimiento/eventos';
const IMPRESIONES = '/api/descubrimiento/impresiones';
const CLAVE_SUJETO = 'sz.descubrimiento.sujeto';

/** Lo que devuelve la ingesta: el sujeto que el cliente debe recordar. */
const SUJETO = '11111111-2222-3333-4444-555555555555';

/**
 * El jsdom de este proyecto no expone un localStorage completo (falta `clear`),
 * asi que se sustituye por uno en memoria. Mismo apanio que en auth.interceptor.spec.
 */
function instalarAlmacenamiento(): void {
  const datos = new Map<string, string>();
  vi.stubGlobal('localStorage', {
    get length() {
      return datos.size;
    },
    clear: () => datos.clear(),
    getItem: (k: string) => datos.get(k) ?? null,
    key: (i: number) => [...datos.keys()][i] ?? null,
    removeItem: (k: string) => void datos.delete(k),
    setItem: (k: string, v: string) => void datos.set(k, String(v)),
  } as Storage);
}

describe('DescubrimientoService', () => {
  let servicio: DescubrimientoService;
  let http: HttpTestingController;

  beforeEach(() => {
    instalarAlmacenamiento();
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(DescubrimientoService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.useRealTimers();
    localStorage.clear();
  });

  /** Fuerza el envío del lote sin esperar los cinco segundos reales. */
  function vaciar(): void {
    vi.advanceTimersByTime(5000);
  }

  /* ══════════════ El tracking no puede estorbar ══════════════ */

  it('si la ingesta falla, NO propaga el error a quien la disparó', () => {
    /*
     * Es la garantía que sostiene todo lo demás: una plataforma que se rompe
     * porque no pudo registrar una métrica tiene las prioridades al revés.
     */
    servicio.vistaDeProducto(9, 4);
    vaciar();

    expect(() => {
      http.expectOne(EVENTOS).flush('caido', { status: 500, statusText: 'Error' });
    }).not.toThrow();
  });

  it('la búsqueda tampoco revienta si el endpoint no está', () => {
    servicio.busqueda('monitor gamer');
    vaciar();
    expect(() => {
      http.expectOne(EVENTOS).error(new ProgressEvent('error'));
    }).not.toThrow();
  });

  /* ══════════════ Nada de eventos duplicados ══════════════ */

  it('ITEM_VIEW se registra UNA vez por producto, aunque se entre varias veces', () => {
    // Volver atrás y reabrir la ficha no es un interés nuevo.
    servicio.vistaDeProducto(9, 4);
    servicio.vistaDeProducto(9, 4);
    servicio.vistaDeProducto(9, 4);
    vaciar();

    const req = http.expectOne(EVENTOS);
    expect(req.request.body.eventos).toHaveLength(1);
    expect(req.request.body.eventos[0].tipo).toBe('ITEM_VIEW');
    req.flush({ sujetoId: SUJETO, registrados: 1 });
  });

  it('SEARCH no se repite con el mismo término', () => {
    /*
     * Recargar la página de resultados o volver atrás vuelve a ejecutar la
     * búsqueda, y sin este filtro cada regreso contaría como una intención
     * nueva.
     */
    servicio.busqueda('monitor gamer');
    servicio.busqueda('monitor gamer');
    servicio.busqueda('  monitor gamer  ');
    vaciar();

    const req = http.expectOne(EVENTOS);
    expect(req.request.body.eventos).toHaveLength(1);
    expect(req.request.body.eventos[0].termino).toBe('monitor gamer');
    req.flush({ sujetoId: SUJETO, registrados: 1 });
  });

  it('un término distinto sí cuenta como búsqueda nueva', () => {
    servicio.busqueda('monitor');
    servicio.busqueda('teclado');
    vaciar();

    const req = http.expectOne(EVENTOS);
    expect(req.request.body.eventos.map((e: { termino: string }) => e.termino)).toEqual([
      'monitor',
      'teclado',
    ]);
    req.flush({ sujetoId: SUJETO, registrados: 2 });
  });

  it('la IMPRESIÓN no se duplica aunque el carrusel se recomponga', () => {
    /*
     * Un carrusel se recompone al desplazarlo. Sin memoria, el mismo producto
     * generaría impresiones sin parar, hundiría el CTR del módulo y —peor—
     * dispararía la fatiga, que lo retiraría de las recomendaciones.
     */
    servicio.impresion('SEGUN_TUS_INTERESES', 9, 0);
    servicio.impresion('SEGUN_TUS_INTERESES', 9, 0);
    servicio.impresion('SEGUN_TUS_INTERESES', 9, 3);
    vaciar();

    const req = http.expectOne(IMPRESIONES);
    expect(req.request.body.impresiones).toHaveLength(1);
    req.flush({ sujetoId: SUJETO, registrados: 1 });
    http.verify();
  });

  it('el mismo producto en OTRO módulo sí es otra impresión', () => {
    // Son medidas distintas: cada carrusel se evalúa por separado.
    servicio.impresion('SEGUN_TUS_INTERESES', 9);
    servicio.impresion('TENDENCIAS', 9);
    vaciar();

    const req = http.expectOne(IMPRESIONES);
    expect(req.request.body.impresiones.map((i: { modulo: string }) => i.modulo)).toEqual([
      'SEGUN_TUS_INTERESES',
      'TENDENCIAS',
    ]);
    req.flush({ sujetoId: SUJETO, registrados: 2 });
  });

  it('las impresiones viajan en UN lote, no una petición por tarjeta', () => {
    /*
     * Es la señal mas voluminosa que existe: un Home con tres carruseles pinta
     * treinta y seis. Saliendo de una en una eran treinta y seis POST, que
     * ademas gastan el mismo cupo de escritura de la pasarela que el carrito y
     * el pedido: quien solo desplazaba la pagina podia acabar sin poder comprar.
     */
    for (let i = 0; i < 12; i++) {
      servicio.impresion('POPULARES', i, i);
    }
    vaciar();

    const req = http.expectOne(IMPRESIONES);
    expect(req.request.body.impresiones).toHaveLength(12);
    req.flush({ sujetoId: SUJETO, registrados: 12 });
    http.verify();
  });

  /* ══════════════ Lotes, no una petición por evento ══════════════ */

  it('los eventos viajan agrupados, no de uno en uno', () => {
    servicio.vistaDeProducto(9, 4);
    servicio.vistaDeCategoria(4);
    servicio.filtroAplicado('pulgadas', '27', 4);

    // Todavía nada: se espera a llenar el buzón o a que pase el plazo.
    http.expectNone(EVENTOS);

    vaciar();
    const req = http.expectOne(EVENTOS);
    expect(req.request.body.eventos).toHaveLength(3);
    req.flush({ sujetoId: SUJETO, registrados: 3 });
  });

  it('el filtro por característica conserva atributo y valor', () => {
    servicio.filtroAplicado('refresco_hz', '165', 4);
    vaciar();

    const req = http.expectOne(EVENTOS);
    expect(req.request.body.eventos[0]).toMatchObject({
      tipo: 'ATTRIBUTE_FILTER',
      atributoCodigo: 'refresco_hz',
      atributoValor: '165',
      categoriaId: 4,
    });
    req.flush({ sujetoId: SUJETO, registrados: 1 });
  });

  /* ══════════════ Tiempo en ficha ══════════════ */

  it('el tiempo en ficha se manda una vez y con techo', () => {
    // Media hora de pestaña olvidada no puede pesar como media hora de lectura.
    servicio.salidaDeProducto(9, 4, 1_800_000);
    vaciar();

    const req = http.expectOne(EVENTOS);
    expect(req.request.body.eventos).toHaveLength(1);
    expect(req.request.body.eventos[0].dwellMs).toBe(180_000);
    req.flush({ sujetoId: SUJETO, registrados: 1 });
  });

  it('un vistazo de menos de un segundo no genera evento', () => {
    servicio.salidaDeProducto(9, 4, 400);
    vaciar();
    http.expectNone(EVENTOS);
  });

  /* ══════════════ Identidad del sujeto ══════════════ */

  it('un visitante anónimo funciona sin identificador previo', () => {
    expect(localStorage.getItem(CLAVE_SUJETO)).toBeNull();

    servicio.vistaDeProducto(9, 4);
    vaciar();

    const req = http.expectOne(EVENTOS);
    // Sin sujeto todavía: lo asigna el servidor, aquí no se inventa ninguno.
    expect(req.request.headers.get('X-Sujeto')).toBeNull();
    req.flush({ sujetoId: SUJETO, registrados: 1 });

    expect(localStorage.getItem(CLAVE_SUJETO)).toBe(SUJETO);
  });

  it('el sujeto devuelto se manda en las peticiones siguientes', () => {
    servicio.vistaDeProducto(9, 4);
    vaciar();
    http.expectOne(EVENTOS).flush({ sujetoId: SUJETO, registrados: 1 });

    servicio.vistaDeProducto(10, 4);
    vaciar();
    const segunda = http.expectOne(EVENTOS);
    expect(segunda.request.headers.get('X-Sujeto')).toBe(SUJETO);
    segunda.flush({ sujetoId: SUJETO, registrados: 1 });
  });

  it('al cerrar sesión se suelta el sujeto para no heredarlo', () => {
    /*
     * Tras iniciar sesión el identificador guardado ES el de la cuenta. Si se
     * quedara, el siguiente visitante de este navegador —un equipo compartido,
     * un locutorio— alimentaría y vería el perfil de quien se fue.
     */
    servicio.vistaDeProducto(9, 4);
    vaciar();
    http.expectOne(EVENTOS).flush({ sujetoId: SUJETO, registrados: 1 });
    expect(servicio.sujeto()).toBe(SUJETO);

    servicio.olvidarSujetoLocal();

    expect(servicio.sujeto()).toBeNull();
    expect(localStorage.getItem(CLAVE_SUJETO)).toBeNull();
  });

  it('tras soltar el sujeto, la memoria de vistas también se limpia', () => {
    // Si no, el visitante nuevo heredaría lo que ya emitió el anterior y sus
    // primeras vistas se perderían en silencio.
    servicio.vistaDeProducto(9, 4);
    vaciar();
    http.expectOne(EVENTOS).flush({ sujetoId: SUJETO, registrados: 1 });

    servicio.olvidarSujetoLocal();

    servicio.vistaDeProducto(9, 4);
    vaciar();
    http.expectOne(EVENTOS).flush({ sujetoId: 'otro', registrados: 1 });
  });

  /* ══════════════ Contexto geográfico ══════════════ */

  it('el ubigeo viaja con el lote cuando se conoce', () => {
    servicio.fijarUbigeo('110101');
    servicio.vistaDeProducto(9, 4);
    vaciar();

    const req = http.expectOne(EVENTOS);
    expect(req.request.body.ubigeo).toBe('110101');
    req.flush({ sujetoId: SUJETO, registrados: 1 });
  });

  it('un ubigeo mal formado se descarta en vez de viajar', () => {
    // Seis dígitos o nada: un prefijo a medias metería al sujeto en otra zona.
    servicio.fijarUbigeo('11');
    servicio.vistaDeProducto(9, 4);
    vaciar();

    const req = http.expectOne(EVENTOS);
    expect(req.request.body.ubigeo).toBeUndefined();
    req.flush({ sujetoId: SUJETO, registrados: 1 });
  });

  /* ══════════════ Lectura ══════════════ */

  it('el Home se pide en UNA petición', () => {
    servicio.home(12).subscribe();
    const req = http.expectOne((r) => r.url === '/api/descubrimiento/home');
    expect(req.request.params.get('porCarrusel')).toBe('12');
    req.flush({ sujetoId: SUJETO, carruseles: [] });
  });

  it('si el Home de descubrimiento falla, devuelve null en vez de romper', () => {
    /*
     * Es lo que permite al componente pintar el catálogo de siempre. El usuario
     * no tiene por qué enterarse de que existe un recomendador, y menos de que
     * se cayó.
     */
    let recibido: unknown = 'sin tocar';
    servicio.home().subscribe((r) => (recibido = r));

    http
      .expectOne((r) => r.url === '/api/descubrimiento/home')
      .flush('caido', { status: 503, statusText: 'Service Unavailable' });

    expect(recibido).toBeNull();
  });

  it('los similares también degradan a null', () => {
    let recibido: unknown = 'sin tocar';
    servicio.similares(9).subscribe((r) => (recibido = r));

    http
      .expectOne((r) => r.url === '/api/descubrimiento/similares/9')
      .flush('caido', { status: 500, statusText: 'Error' });

    expect(recibido).toBeNull();
  });

  it('«no me interesa» sale al instante, sin esperar al lote', () => {
    // La tarjeta se retira en el acto; si esperara al plazo y el usuario
    // recargara antes, el producto reaparecería.
    servicio.noMeInteresa(13, 4, 'SEGUN_TUS_INTERESES').subscribe();

    const req = http.expectOne(EVENTOS);
    expect(req.request.body.eventos[0].tipo).toBe('NOT_INTERESTED');
    expect(req.request.body.eventos[0].itemId).toBe(13);
    req.flush({ sujetoId: SUJETO, registrados: 1 });
  });
});
