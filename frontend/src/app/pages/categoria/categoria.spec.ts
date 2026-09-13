import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  CategoriaService,
  DescubrimientoService,
  ProductoService,
} from '../../core';
import { Categoria } from './categoria';

const CATEGORIA = { id: 5, name: 'Laptops', slug: 'laptops', description: '', icono: null };
const FACETAS = { total: 0, disponibles: 0, marcas: [], atributos: [] };

function pagina(total = 0, totalPages = 0, content: unknown[] = []) {
  return { content, number: 0, size: 12, totalElements: total, totalPages };
}

describe('Categoria (página) · filtrado en servidor', () => {
  let paramMap: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let queryParamMap: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let listarPorCategoria: ReturnType<typeof vi.fn>;
  let facetas: ReturnType<typeof vi.fn>;
  let filtroAplicado: ReturnType<typeof vi.fn>;

  function crear() {
    const fixture = TestBed.createComponent(Categoria);
    fixture.detectChanges();
    const router = TestBed.inject(Router);
    const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    return { fixture, comp: fixture.componentInstance as any, navegar };
  }

  beforeEach(() => {
    paramMap = new BehaviorSubject(convertToParamMap({ slug: 'laptops' }));
    queryParamMap = new BehaviorSubject(convertToParamMap({}));
    listarPorCategoria = vi.fn(() => of(pagina()));
    facetas = vi.fn(() => of(FACETAS));
    filtroAplicado = vi.fn();

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap, queryParamMap } },
        { provide: CategoriaService, useValue: { obtenerPorSlug: () => of(CATEGORIA) } },
        {
          provide: ProductoService,
          useValue: { listarPorCategoria, facetas },
        },
        {
          provide: DescubrimientoService,
          useValue: { vistaDeCategoria: vi.fn(), filtroAplicado },
        },
      ],
    });
  });

  it('al entrar consulta la primera página de la categoría', () => {
    crear();
    expect(listarPorCategoria).toHaveBeenCalledWith('laptops', 0, 12, expect.any(Object));
    // Las facetas se piden dos veces: universo (sin filtro) y conteos (con él).
    expect(facetas).toHaveBeenCalled();
  });

  it('reconstruye el filtro desde la URL (persistencia)', () => {
    queryParamMap.next(
      convertToParamMap({ marca: ['2', '3'], orden: 'precio-asc', page: '1', disp: '1' }),
    );
    const { comp } = crear();

    const f = comp.filtro();
    expect(f.marcaIds).toEqual([2, 3]);
    expect(f.orden).toBe('precio-asc');
    expect(f.soloDisponibles).toBe(true);
    expect(comp.paginaActual()).toBe(1);
    // Y esa página filtrada es la que se pide al servidor.
    expect(listarPorCategoria).toHaveBeenLastCalledWith(
      'laptops',
      1,
      12,
      expect.objectContaining({ marcaIds: [2, 3], orden: 'precio-asc' }),
    );
  });

  it('cambiar un filtro navega a la URL con ese filtro y vuelve a la página 0', () => {
    const { comp, navegar } = crear();

    comp.alFiltrar({
      precioMin: null,
      precioMax: null,
      marcaIds: [7],
      atributos: [],
      soloDisponibles: false,
      orden: 'relevancia',
    });

    expect(navegar).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { marca: [7] } }),
    );
  });

  it('paginar navega conservando el filtro y con el número de página', () => {
    listarPorCategoria.mockReturnValue(of(pagina(40, 4)));
    const { comp, navegar } = crear();

    comp.irAPagina(2);

    expect(navegar).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: expect.objectContaining({ page: 2 }) }),
    );
  });

  it('conjunto vacío: ni total ni productos', () => {
    listarPorCategoria.mockReturnValue(of(pagina(0, 0)));
    const { comp } = crear();
    expect(comp.productos()).toEqual([]);
    expect(comp.totalElementos()).toBe(0);
  });

  it('aplicar un atributo registra el ATTRIBUTE_FILTER con la categoría', () => {
    const { comp } = crear();
    comp.alAtributo({ codigo: 'ram_gb', valor: '16' });
    expect(filtroAplicado).toHaveBeenCalledWith('ram_gb', '16', 5);
  });
});
