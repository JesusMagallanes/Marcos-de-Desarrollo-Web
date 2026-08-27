import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { CategoriaService } from './categoria.service';
import { CacheLecturaService } from '../../offline';

describe('CategoriaService', () => {
  let service: CategoriaService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        CategoriaService,
        {
          provide: CacheLecturaService,
          useValue: {
            obtener: (_key: string, _ttl: number, fn: () => unknown) => fn(),
            invalidar: () => Promise.resolve(),
          },
        },
      ],
    });
    service = TestBed.inject(CategoriaService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('listar() llama a GET /api/categorias', () => {
    const mock = [
      { id: 1, name: 'Laptops', slug: 'laptops', description: 'Portátiles' },
    ];

    service.listar().subscribe((cats) => {
      expect(cats.length).toBe(1);
      expect(cats[0].name).toBe('Laptops');
    });

    const req = httpMock.expectOne('/api/categorias');
    expect(req.request.method).toBe('GET');
    req.flush(mock);
  });

  it('obtener() llama a GET /api/categorias/{id}', () => {
    const mock = { id: 1, name: 'Laptops', slug: 'laptops', description: 'Portátiles' };

    service.obtener(1).subscribe((cat) => {
      expect(cat.id).toBe(1);
    });

    const req = httpMock.expectOne('/api/categorias/1');
    expect(req.request.method).toBe('GET');
    req.flush(mock);
  });

  it('obtenerPorSlug() llama a GET /api/categorias/slug/{slug}', () => {
    const mock = { id: 1, name: 'Laptops', slug: 'laptops', description: 'Portátiles' };

    service.obtenerPorSlug('laptops').subscribe((cat) => {
      expect(cat.slug).toBe('laptops');
    });

    const req = httpMock.expectOne('/api/categorias/slug/laptops');
    expect(req.request.method).toBe('GET');
    req.flush(mock);
  });

  it('crear() llama a POST /api/categorias', () => {
    const dto = { name: 'Monitores', slug: 'monitores', description: 'Pantallas', icono: 'desktop' };
    const mock = { id: 2, ...dto };

    service.crear(dto).subscribe((cat) => {
      expect(cat.id).toBe(2);
    });

    const req = httpMock.expectOne('/api/categorias');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(dto);
    req.flush(mock);
  });

  it('eliminar() llama a DELETE /api/categorias/{id}', () => {
    service.eliminar(1).subscribe();

    const req = httpMock.expectOne('/api/categorias/1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
