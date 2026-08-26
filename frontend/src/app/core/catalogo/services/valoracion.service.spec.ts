import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { bd } from '../../offline';
import { esperarPeticion } from '../../shared/testing/esperar-peticion';
import { Valoracion } from '../models';
import { ValoracionService } from './valoracion.service';

function valoracion(id: number, estado: Valoracion['estado'] = 'APROBADA'): Valoracion {
  return {
    id,
    nombre: 'Cliente Uno',
    calificacion: 5,
    comentario: 'Excelente.',
    estado,
    creadoEn: '2026-08-01T12:00:00Z',
  };
}

describe('ValoracionService', () => {
  let servicio: ValoracionService;
  let http: HttpTestingController;

  beforeEach(async () => {
    await Promise.all([bd.cache.clear(), bd.cola.clear()]);
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(ValoracionService);
    http = TestBed.inject(HttpTestingController);
  });

  it('listar() pide las valoraciones de un producto', async () => {
    servicio.listar(1).subscribe();

    const req = await esperarPeticion(http, '/api/productos/1/valoraciones');
    expect(req.request.method).toBe('GET');
    req.flush([valoracion(1)]);
  });

  it('mia() pide la valoración del usuario en curso', () => {
    servicio.mia(1).subscribe();

    const req = http.expectOne('/api/productos/1/valoraciones/mia');
    expect(req.request.method).toBe('GET');
    req.flush(null);
  });

  it('guardar() hace POST con el cuerpo enviado', () => {
    servicio
      .guardar(1, { calificacion: 4, comentario: 'Muy bueno.', nombre: 'Cliente Uno' })
      .subscribe();

    const req = http.expectOne('/api/productos/1/valoraciones');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      calificacion: 4,
      comentario: 'Muy bueno.',
      nombre: 'Cliente Uno',
    });
    req.flush(valoracion(1, 'PENDIENTE'));
  });

  it('eliminar() borra la valoración del usuario en curso', () => {
    servicio.eliminar(1).subscribe();

    const req = http.expectOne({ url: '/api/productos/1/valoraciones/mia', method: 'DELETE' });
    req.flush(null);
  });

  it('destacadas() pide las mejores valoradas de la portada', async () => {
    servicio.destacadas().subscribe();

    const req = await esperarPeticion(http, '/api/valoraciones/top');
    expect(req.request.method).toBe('GET');
    req.flush([
      {
        id: 20,
        nombre: 'Pamela',
        calificacion: 5,
        comentario: 'Excelente.',
        estado: 'APROBADA',
        creadoEn: '2026-08-01T12:00:00Z',
        productoId: 7,
        productoNombre: 'Asus ROG Strix G16',
        productoImagenUrl: 'https://img.example/rog-g16.jpg',
      },
    ]);
  });

  it('listarAdmin() sin filtro pide todas las valoraciones', () => {
    servicio.listarAdmin().subscribe();

    const req = http.expectOne('/api/valoraciones/admin');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys()).toEqual([]);
    req.flush([valoracion(1, 'PENDIENTE')]);
  });

  it('listarAdmin(estado) filtra por estado en la query', () => {
    servicio.listarAdmin('PENDIENTE').subscribe();

    const req = http.expectOne('/api/valoraciones/admin?estado=PENDIENTE');
    expect(req.request.method).toBe('GET');
    req.flush([valoracion(1, 'PENDIENTE')]);
  });

  it('cambiarEstado() hace PATCH con el estado nuevo', () => {
    servicio.cambiarEstado(5, 'APROBADA').subscribe();

    const req = http.expectOne('/api/valoraciones/admin/5/estado');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ estado: 'APROBADA' });
    req.flush(valoracion(5, 'APROBADA'));
  });

  it('eliminarAdmin() borra la valoración señalada', () => {
    servicio.eliminarAdmin(5).subscribe();

    const req = http.expectOne({ url: '/api/valoraciones/admin/5', method: 'DELETE' });
    req.flush(null);
  });

  it('guardar() SIN conexión encola la operación y responde un eco optimista, sin tocar la red', async () => {
    // jsdom dice estar online. Se fuerza offline ANTES de crear los servicios:
    // ConexionService lee el estado del navegador al construirse.
    const estadoReal = Object.getOwnPropertyDescriptor(navigator, 'onLine');
    Object.defineProperty(navigator, 'onLine', { value: false, configurable: true });

    try {
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [provideHttpClient(), provideHttpClientTesting()],
      });
      const servicioOffline = TestBed.inject(ValoracionService);
      const httpOffline = TestBed.inject(HttpTestingController);

      let eco: Valoracion | undefined;
      servicioOffline
        .guardar(1, { calificacion: 4, comentario: 'Muy bueno.', nombre: 'Cliente Uno' })
        .subscribe((v) => (eco = v));

      await vi.waitFor(async () => expect((await bd.cola.toArray()).length).toBe(1));

      expect(eco).toBeDefined();
      expect(eco!.id).toBeLessThan(0); // eco optimista, no una reseña real
      expect(eco!.estado).toBe('PENDIENTE');

      const fila = (await bd.cola.toArray())[0];
      expect(fila.estado).toBe('PENDIENTE');
      expect(fila.claveEntidad).toBe('valoracion:1');
      httpOffline.verify(); // ni una sola petición HTTP
    } finally {
      if (estadoReal) {
        Object.defineProperty(navigator, 'onLine', estadoReal);
      } else {
        delete (navigator as any).onLine;
      }
      // La siguiente prueba rearma su propio inyector en el beforeEach.
      TestBed.resetTestingModule();
    }
  });
});
