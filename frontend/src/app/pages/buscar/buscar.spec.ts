import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DescubrimientoService, ProductoService } from '../../core';
import { Buscar } from './buscar';

const FACETAS = { total: 0, disponibles: 0, marcas: [], atributos: [] };

function pagina(total = 0, totalPages = 0, content: unknown[] = []) {
  return { content, number: 0, size: 24, totalElements: total, totalPages };
}

describe('Buscar (página) · filtrado en servidor', () => {
  let queryParamMap: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let listar: ReturnType<typeof vi.fn>;
  let facetas: ReturnType<typeof vi.fn>;
  let busqueda: ReturnType<typeof vi.fn>;

  function crear() {
    const fixture = TestBed.createComponent(Buscar);
    fixture.detectChanges();
    const router = TestBed.inject(Router);
    const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    return { fixture, comp: fixture.componentInstance as any, navegar };
  }

  beforeEach(() => {
    queryParamMap = new BehaviorSubject(convertToParamMap({ q: 'lg' }));
    listar = vi.fn(() => of(pagina()));
    facetas = vi.fn(() => of(FACETAS));
    busqueda = vi.fn();

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { queryParamMap } },
        { provide: ProductoService, useValue: { listar, facetas } },
        { provide: DescubrimientoService, useValue: { busqueda, filtroAplicado: vi.fn() } },
      ],
    });
  });

  it('busca el término de la URL y registra SEARCH una vez', () => {
    crear();
    expect(listar).toHaveBeenCalledWith('lg', 0, 24, expect.any(Object));
    expect(busqueda).toHaveBeenCalledWith('lg');
    expect(busqueda).toHaveBeenCalledTimes(1);
  });

  it('cambiar un filtro NO cuenta como una búsqueda nueva', () => {
    const { comp } = crear();
    busqueda.mockClear();

    // Llega el mismo término con un filtro más (como haría el propio navegar).
    queryParamMap.next(convertToParamMap({ q: 'lg', marca: '4' }));

    expect(busqueda).not.toHaveBeenCalled();
    expect(comp.filtro().marcaIds).toEqual([4]);
    expect(listar).toHaveBeenLastCalledWith(
      'lg',
      0,
      24,
      expect.objectContaining({ marcaIds: [4] }),
    );
  });

  it('filtrar conserva el término en la URL', () => {
    const { comp, navegar } = crear();

    comp.alFiltrar({
      precioMin: 100,
      precioMax: null,
      marcaIds: [],
      atributos: [],
      soloDisponibles: false,
      orden: 'relevancia',
    });

    expect(navegar).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { q: 'lg', precioMin: 100 } }),
    );
  });

  it('conjunto vacío: sin resultados', () => {
    listar.mockReturnValue(of(pagina(0, 0)));
    const { comp } = crear();
    expect(comp.resultados()).toEqual([]);
    expect(comp.totalEncontrados()).toBe(0);
  });
});
