import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComparacionService, MAX_COMPARAR } from './comparacion.service';

describe('ComparacionService', () => {
  beforeEach(() => {
    // Mismo apaño que auth.service.spec: el localStorage de jsdom aquí está incompleto.
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
    });
    TestBed.resetTestingModule();
  });

  const producto = (id: number) => ({ id, name: 'P' + id, imageUrl: null });

  it('añade y quita con el mismo gesto, y recuerda la lista al recargar', () => {
    const lista = TestBed.inject(ComparacionService);

    lista.alternar(producto(1));
    lista.alternar(producto(2));
    lista.alternar(producto(1));

    expect(lista.ids()).toEqual([2]);
    TestBed.resetTestingModule();
    expect(TestBed.inject(ComparacionService).ids()).toEqual([2]);
  });

  it('no pasa de tres: la tabla dejaría de leerse', () => {
    const lista = TestBed.inject(ComparacionService);
    for (let id = 1; id <= MAX_COMPARAR; id++) lista.alternar(producto(id));

    expect(lista.alternar(producto(99))).toBe(false);
    expect(lista.ids()).toHaveLength(MAX_COMPARAR);
  });

  it('ignora lo que no tenga forma de producto en el almacenamiento', () => {
    localStorage.setItem('sz_comparar', JSON.stringify([{ id: 'x' }, { id: 4, name: 'Bien', imageUrl: null }]));

    expect(TestBed.inject(ComparacionService).ids()).toEqual([4]);
  });
});
