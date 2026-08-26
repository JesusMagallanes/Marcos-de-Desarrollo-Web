import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { bd } from './db';
import { esperarPeticion } from '../shared/testing/esperar-peticion';
import { ColaSyncService } from './cola-sync.service';

/**
 * La cola es la pieza donde un bug se paga con dinero ajeno: una reseña
 * perdida o duplicada. Estas pruebas cubren exactamente los cuatro caminos
 * que puede tomar una operación al sincronizar.
 */
describe('ColaSyncService', () => {
  let cola: ColaSyncService;
  let http: HttpTestingController;

  const URL_SYNC = '/api/sync/valoraciones';

  function encolarGuardado(productoId: number, calificacion = 5) {
    return cola.encolar({
      tipo: 'VALORACION_GUARDAR',
      claveEntidad: `valoracion:${productoId}`,
      url: URL_SYNC,
      cuerpoBase: { tipo: 'GUARDAR', productoId, valoracion: { calificacion } },
    });
  }

  beforeEach(async () => {
    await Promise.all([bd.cache.clear(), bd.cola.clear()]);
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    cola = TestBed.inject(ColaSyncService);
    http = TestBed.inject(HttpTestingController);
  });

  it('encolar dos veces la MISMA entidad sustituye la pendiente: no se duplica', async () => {
    await encolarGuardado(1, 3);
    await encolarGuardado(1, 5);

    const filas = await bd.cola.toArray();
    expect(filas.length).toBe(1);
    expect((filas[0].cuerpo as any).valoracion.calificacion).toBe(5);
    expect(cola.pendientes()).toBe(1);
  });

  it('entidades distintas SÍ se acumulan y salen en orden FIFO', async () => {
    await encolarGuardado(2);
    await encolarGuardado(3);

    (await esperarPeticion(http, URL_SYNC)).flush({ duplicado: false, valoracionId: 11 });
    // La segunda sale solo cuando la primera terminó: el barrido es secuencial.
    (await esperarPeticion(http, URL_SYNC)).flush({ duplicado: false, valoracionId: 12 });

    await vi.waitFor(() => expect(cola.pendientes()).toBe(0));
  });

  it('el envío viaja con el operacionId de la fila (idempotencia)', async () => {
    await encolarGuardado(9);

    const req = await esperarPeticion(http, URL_SYNC);
    const cuerpo = req.request.body as Record<string, unknown>;
    expect(cuerpo['operacionId']).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
    );
    // El id de la fila coincide con el que viaja: reintentos reutilizan el mismo.
    const fila = (await bd.cola.toArray())[0];
    expect(fila.operacionId).toBe(cuerpo['operacionId']);

    req.flush({ duplicado: false, valoracionId: 1 });
  });

  it('"ya estaba" del servidor también saca la operación de la cola', async () => {
    await encolarGuardado(4);

    const req = await esperarPeticion(http, URL_SYNC);
    req.flush({ duplicado: true, valoracionId: null });

    await vi.waitFor(() => expect(cola.pendientes()).toBe(0));
  });

  it('un rechazo DEFINITIVO marca RECHAZADA y deja pasar las siguientes', async () => {
    await encolarGuardado(5); // será rechazada: p.ej. no compró el producto
    await encolarGuardado(6);

    const reqMala = await esperarPeticion(http, URL_SYNC);
    reqMala.flush(
      { detail: 'Solo puedes valorar productos que hayas comprado.' },
      { status: 409, statusText: 'Conflict' },
    );
    (await esperarPeticion(http, URL_SYNC)).flush({ duplicado: false, valoracionId: 13 });

    await vi.waitFor(() => expect(cola.rechazadas().length).toBe(1));
    expect(cola.pendientes()).toBe(0);
    expect(cola.rechazadas()[0].ultimoError).toContain('comprado');
  });

  it('un fallo TRANSITORIO conserva la operación y corta el barrido', async () => {
    await encolarGuardado(7);

    const req = await esperarPeticion(http, URL_SYNC);
    req.flush(null, { status: 503, statusText: 'Service Unavailable' });

    await vi.waitFor(() => expect(cola.sincronizando()).toBe(false));
    expect(cola.pendientes()).toBe(1);

    const fila = (await bd.cola.toArray())[0];
    expect(fila.estado).toBe('PENDIENTE');
    expect(fila.intentos).toBe(1);
  });
});
