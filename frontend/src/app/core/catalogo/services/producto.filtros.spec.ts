import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { bd } from '../../offline';
import { esperarPeticion } from '../../shared/testing/esperar-peticion';
import { FiltroCatalogo, filtroVacio } from '../models';
import { ProductoService } from './producto.service';

function paginaVacia(size = 12) {
  return { content: [], number: 0, size, totalElements: 0, totalPages: 0 };
}

const FACETAS_VACIAS = { total: 0, disponibles: 0, marcas: [], atributos: [] };

/** Un filtro con algo de cada clase, para comprobar cómo viaja completo. */
function filtroLleno(): FiltroCatalogo {
  return {
    precioMin: 100,
    precioMax: 2000,
    marcaIds: [3, 7],
    atributos: ['ram_gb:16', 'ram_gb:32', 'pulgadas:27'],
    soloDisponibles: true,
    orden: 'precio-asc',
  };
}

describe('ProductoService · filtros y facetas', () => {
  let servicio: ProductoService;
  let http: HttpTestingController;

  beforeEach(async () => {
    await Promise.all([bd.cache.clear(), bd.cola.clear()]);
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(ProductoService);
    http = TestBed.inject(HttpTestingController);
  });

  /* ══════════════ Construcción de parámetros ══════════════ */

  it('sin filtro, el listado viaja exactamente como antes', async () => {
    // Compatibilidad: el contrato viejo no debe cambiar por añadir filtros.
    servicio.listar('lg', 0, 24).subscribe();
    const req = http.expectOne('/api/productos?page=0&size=24&search=lg');
    expect(req.request.params.has('precioMin')).toBe(false);
    expect(req.request.params.has('orden')).toBe(false);
    req.flush(paginaVacia(24));
  });

  it('el filtro completo viaja con la convención del backend', () => {
    servicio.listar('lg', 0, 24, filtroLleno()).subscribe();

    const req = http.expectOne((r) => r.url === '/api/productos');
    const p = req.request.params;
    expect(p.get('search')).toBe('lg');
    expect(p.get('precioMin')).toBe('100');
    expect(p.get('precioMax')).toBe('2000');
    // Marcas y atributos se REPITEN, no se juntan en una cadena.
    expect(p.getAll('marcaId')).toEqual(['3', '7']);
    expect(p.getAll('atributo')).toEqual(['ram_gb:16', 'ram_gb:32', 'pulgadas:27']);
    expect(p.get('soloDisponibles')).toBe('true');
    // El orden se traduce al enumerado: precio-asc → PRECIO_ASC.
    expect(p.get('orden')).toBe('PRECIO_ASC');
    req.flush(paginaVacia(24));
  });

  it('el orden por defecto (relevancia) no ensucia la URL', () => {
    servicio.listar(null, 0, 12, { ...filtroVacio(), marcaIds: [5] }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/productos');
    expect(req.request.params.has('orden')).toBe(false);
    expect(req.request.params.getAll('marcaId')).toEqual(['5']);
    req.flush(paginaVacia());
  });

  it('la categoría acepta los mismos filtros', () => {
    servicio.listarPorCategoria('laptops', 1, 12, filtroLleno()).subscribe();
    const req = http.expectOne((r) => r.url === '/api/productos/categoria/laptops');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.getAll('marcaId')).toEqual(['3', '7']);
    req.flush(paginaVacia());
  });

  /* ══════════════ Facetas ══════════════ */

  it('facetas() pega a /facetas con texto y filtro', () => {
    servicio.facetas({ buscar: 'lg', filtro: filtroLleno() }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/productos/facetas');
    expect(req.request.params.get('search')).toBe('lg');
    expect(req.request.params.getAll('atributo')).toEqual([
      'ram_gb:16',
      'ram_gb:32',
      'pulgadas:27',
    ]);
    req.flush(FACETAS_VACIAS);
  });

  it('facetas() por categoría manda el slug', () => {
    servicio.facetas({ slug: 'laptops' }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/productos/facetas');
    expect(req.request.params.get('slug')).toBe('laptops');
    req.flush(FACETAS_VACIAS);
  });

  /* ══════════════ Caché ══════════════ */

  it('una categoría filtrada NO se cachea: vuelve a pedir', () => {
    const filtro = { ...filtroVacio(), marcaIds: [3] };
    servicio.listarPorCategoria('laptops', 0, 12, filtro).subscribe();
    http.expectOne((r) => r.url === '/api/productos/categoria/laptops').flush(paginaVacia());

    // Sin caché, la segunda llamada dispara otra petición (fallaría por
    // petición inesperada si se hubiera cacheado).
    servicio.listarPorCategoria('laptops', 0, 12, filtro).subscribe();
    http.expectOne((r) => r.url === '/api/productos/categoria/laptops').flush(paginaVacia());
  });

  it('un orden distinto del de por defecto también salta la caché', () => {
    const filtro = { ...filtroVacio(), orden: 'nombre-asc' as const };
    servicio.listarPorCategoria('laptops', 0, 12, filtro).subscribe();
    http.expectOne((r) => r.url === '/api/productos/categoria/laptops').flush(paginaVacia());

    servicio.listarPorCategoria('laptops', 0, 12, filtro).subscribe();
    http.expectOne((r) => r.url === '/api/productos/categoria/laptops').flush(paginaVacia());
  });

  it('la categoría SIN filtros sigue cacheando', async () => {
    servicio.listarPorCategoria('laptops', 0, 12).subscribe();
    (await esperarPeticion(http, '/api/productos/categoria/laptops?page=0&size=12')).flush(
      paginaVacia(),
    );

    await vi.waitFor(async () => {
      expect(await bd.cache.get('productos:cat:laptops:0:12')).toBeDefined();
    });

    servicio.listarPorCategoria('laptops', 0, 12).subscribe();
    http.verify(); // la segunda lectura sale de la caché
  });
});
