import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { bd } from './db';
import { esperarPeticion } from '../shared/testing/esperar-peticion';
import { CacheLecturaService } from './cache-lectura.service';

/**
 * Pruebas de la caché offline-first. Con el polyfill de IndexedDB del setup
 * global el comportamiento es real: escrituras, TTL y generaciones incluidas.
 */
describe('CacheLecturaService', () => {
  let cache: CacheLecturaService;
  let http: HttpTestingController;
  let httpClient: HttpClient;

  beforeEach(async () => {
    await Promise.all([bd.cache.clear(), bd.cola.clear()]);
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    cache = TestBed.inject(CacheLecturaService);
    http = TestBed.inject(HttpTestingController);
    httpClient = TestBed.inject(HttpClient);
  });

  it('sin dato local va a la red y guarda la respuesta', async () => {
    const promesa = firstValueFrom(
      cache.obtener('productos:todas', 60_000, () =>
        httpClient.get<string[]>('/api/productos'),
      ),
    );

    (await esperarPeticion(http, '/api/productos')).flush([{ id: 1 }]);
    await expect(promesa).resolves.toEqual([{ id: 1 }]);

    // El guardado es "dispara y olvida": puede aterrizar un pelín después.
    await vi.waitFor(async () => {
      const entrada = await bd.cache.get('productos:todas');
      expect(entrada?.valor).toEqual([{ id: 1 }]);
    });
  });

  it('con dato fresco NO vuelve a la red', async () => {
    await bd.cache.put({
      clave: 'marcas:todas',
      valor: [{ id: 7 }],
      guardadoEn: Date.now(),
      expiraEn: Date.now() + 60_000,
      generacion: 0,
    });

    const resultado = await firstValueFrom(
      cache.obtener<unknown[]>('marcas:todas', 60_000, () => {
        throw new Error('no debió ir a la red');
      }),
    );

    expect(resultado).toEqual([{ id: 7 }]);
    http.verify();
  });

  it('con dato rancio sirve primero lo local y refresca detrás (stale-while-revalidate)', async () => {
    await bd.cache.put({
      clave: 'productos:todas',
      valor: ['rancio'],
      guardadoEn: Date.now() - 120_000,
      expiraEn: Date.now() - 1000,
      generacion: 0,
    });

    const vistos: unknown[] = [];
    const termino = cache
      .obtener<string[]>('productos:todas', 60_000, () =>
        httpClient.get<string[]>('/api/productos'),
      )
      .subscribe((v) => vistos.push(v));

    (await esperarPeticion(http, '/api/productos')).flush(['fresco']);
    termino.unsubscribe();

    // Primero la copia local sin esperar a la nube, después el dato fresco.
    // Cada emisión es el arreglo completo guardado, por eso van anidadas.
    expect(vistos).toEqual([['rancio'], ['fresco']]);
  });

  it('si la red falla con copia rancia disponible, entrega la rancia y no un error', async () => {
    await bd.cache.put({
      clave: 'productos:todas',
      valor: ['rancio'],
      guardadoEn: Date.now() - 120_000,
      expiraEn: Date.now() - 1000,
      generacion: 0,
    });

    const vistos: unknown[] = [];
    const termino = cache
      .obtener<string[]>('productos:todas', 60_000, () =>
        httpClient.get<string[]>('/api/productos'),
      )
      .subscribe((v) => vistos.push(v));

    (await esperarPeticion(http, '/api/productos')).flush(null, {
      status: 503,
      statusText: 'Service Unavailable',
    });
    termino.unsubscribe();

    /*
     * OJO: aquí NO sirve firstValueFrom — al quedarse con la primera emisión
     * se da de baja y concat jamás llega a suscribir la pata de red. La
     * suscripción viva recibe primero lo local y luego el respaldo tras el 503.
     */
    expect(vistos[0]).toEqual(['rancio']);
  });

  it('invalidar() vence la entrada aunque su TTL siga vivo', async () => {
    await bd.cache.put({
      clave: 'categorias:todas',
      valor: ['vieja'],
      guardadoEn: Date.now(),
      expiraEn: Date.now() + 3_600_000,
      generacion: 0,
    });
    await cache.invalidar('categorias');

    const flujo = cache.obtener<string[]>('categorias:todas', 3_600_000, () =>
      httpClient.get<string[]>('/api/categorias'),
    );
    const promesa = firstValueFrom(flujo);

    (await esperarPeticion(http, '/api/categorias')).flush(['nueva']);
    await expect(promesa).resolves.toEqual(['nueva']);
  });
});
