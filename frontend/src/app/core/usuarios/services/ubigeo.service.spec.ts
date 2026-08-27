import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { UbigeoService } from './ubigeo.service';

describe('UbigeoService', () => {
  let service: UbigeoService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), UbigeoService],
    });
    service = TestBed.inject(UbigeoService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('departamentos() llama a GET /api/ubigeo/departamentos', () => {
    service.departamentos().subscribe((deps) => {
      expect(deps.length).toBe(2);
    });
    httpMock.expectOne('/api/ubigeo/departamentos').flush(['Lima', 'Arequipa']);
  });

  it('provincias() llama a GET /api/ubigeo/provincias con departamento', () => {
    service.provincias('Lima').subscribe();
    const req = httpMock.expectOne('/api/ubigeo/provincias?departamento=Lima');
    expect(req.request.method).toBe('GET');
    req.flush(['Lima', 'Cañete']);
  });

  it('provincias() sin departamento devuelve array vacío sin HTTP', () => {
    service.provincias('').subscribe((provs) => {
      expect(provs).toEqual([]);
    });
    httpMock.expectNone('/api/ubigeo/provincias');
  });

  it('distritos() llama a GET /api/ubigeo/distritos con departamento y provincia', () => {
    service.distritos('Lima', 'Lima').subscribe();
    const req = httpMock.expectOne('/api/ubigeo/distritos?departamento=Lima&provincia=Lima');
    expect(req.request.method).toBe('GET');
    req.flush(['Miraflores', 'San Isidro']);
  });

  it('distritos() sin departamento devuelve array vacío', () => {
    service.distritos('', 'Lima').subscribe((d) => expect(d).toEqual([]));
    httpMock.expectNone('/api/ubigeo/distritos');
  });

  it('distritos() sin provincia devuelve array vacío', () => {
    service.distritos('Lima', '').subscribe((d) => expect(d).toEqual([]));
    httpMock.expectNone('/api/ubigeo/distritos');
  });
});
