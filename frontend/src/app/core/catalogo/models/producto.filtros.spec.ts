import { describe, expect, it } from 'vitest';
import {
  FacetasCatalogo,
  filtroActivo,
  filtroVacio,
  fusionarFacetas,
  ordenBackend,
} from './producto.model';

describe('Filtro de catálogo · helpers', () => {
  it('filtroVacio no filtra nada', () => {
    expect(filtroActivo(filtroVacio())).toBe(false);
  });

  it('filtroActivo reconoce cada clase de filtro, pero no el orden', () => {
    expect(filtroActivo({ ...filtroVacio(), precioMin: 10 })).toBe(true);
    expect(filtroActivo({ ...filtroVacio(), marcaIds: [1] })).toBe(true);
    expect(filtroActivo({ ...filtroVacio(), atributos: ['ram_gb:16'] })).toBe(true);
    expect(filtroActivo({ ...filtroVacio(), soloDisponibles: true })).toBe(true);
    // El orden no es un filtro: no recorta el conjunto.
    expect(filtroActivo({ ...filtroVacio(), orden: 'precio-asc' })).toBe(false);
  });

  it('ordenBackend traduce al enumerado del servidor', () => {
    expect(ordenBackend('relevancia')).toBe('RELEVANCIA');
    expect(ordenBackend('precio-asc')).toBe('PRECIO_ASC');
    expect(ordenBackend('nombre-desc')).toBe('NOMBRE_DESC');
    expect(ordenBackend('novedad')).toBe('NOVEDAD');
  });

  /* ══════════════ Fusión universo + conteos ══════════════ */

  const universo: FacetasCatalogo = {
    total: 30,
    disponibles: 28,
    marcas: [
      { id: 1, nombre: 'Lenovo', conteo: 20 },
      { id: 2, nombre: 'ASUS', conteo: 10 },
    ],
    atributos: [
      {
        codigo: 'ram_gb',
        nombre: 'RAM',
        valores: [
          { valor: '16', conteo: 18 },
          { valor: '32', conteo: 12 },
        ],
      },
    ],
  };

  it('las opciones salen del universo y el conteo, del conjunto filtrado', () => {
    const conteos: FacetasCatalogo = {
      total: 20,
      disponibles: 20,
      marcas: [{ id: 1, nombre: 'Lenovo', conteo: 20 }],
      atributos: [{ codigo: 'ram_gb', nombre: 'RAM', valores: [{ valor: '16', conteo: 20 }] }],
    };

    const vista = fusionarFacetas(universo, conteos)!;

    // El total es el del conjunto ya filtrado.
    expect(vista.total).toBe(20);
    // Las DOS marcas siguen ofreciéndose (multi-select), aunque una esté a cero:
    // si desapareciera no se podría añadir como alternativa.
    expect(vista.marcas.map((m) => m.nombre)).toEqual(['Lenovo', 'ASUS']);
    expect(vista.marcas.find((m) => m.id === 2)!.conteo).toBe(0);
    expect(vista.marcas.find((m) => m.id === 1)!.conteo).toBe(20);
    // Igual con los valores del atributo: el 32 se queda visible, a cero.
    const ram = vista.atributos[0].valores;
    expect(ram.find((v) => v.valor === '16')!.conteo).toBe(20);
    expect(ram.find((v) => v.valor === '32')!.conteo).toBe(0);
  });

  it('sin universo devuelve los conteos tal cual', () => {
    expect(fusionarFacetas(null, universo)).toBe(universo);
  });
});
