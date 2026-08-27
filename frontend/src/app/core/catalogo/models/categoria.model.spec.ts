import { describe, it, expect } from 'vitest';
import { iconoCategoria, generarSlug, PATRON_SLUG, ICONO_CATEGORIA_POR_DEFECTO } from './categoria.model';

describe('categoria.model', () => {
  describe('iconoCategoria', () => {
    it('devuelve el icono de la categoría con prefijo fa-solid', () => {
      expect(iconoCategoria({ icono: 'laptop' })).toBe('fa-solid fa-laptop');
    });

    it('devuelve el icono por defecto si icono es null', () => {
      expect(iconoCategoria({ icono: null })).toBe(`fa-solid fa-${ICONO_CATEGORIA_POR_DEFECTO}`);
    });

    it('devuelve el icono por defecto si icono es vacío', () => {
      expect(iconoCategoria({ icono: '' })).toBe(`fa-solid fa-${ICONO_CATEGORIA_POR_DEFECTO}`);
    });

    it('trimea el icono', () => {
      expect(iconoCategoria({ icono: '  laptop  ' })).toBe('fa-solid fa-laptop');
    });
  });

  describe('PATRON_SLUG', () => {
    it('acepta slugs válidos', () => {
      expect(PATRON_SLUG.test('laptops')).toBe(true);
      expect(PATRON_SLUG.test('monitores-pro')).toBe(true);
      expect(PATRON_SLUG.test('test-123')).toBe(true);
    });

    it('rechaza slugs con mayúsculas', () => {
      expect(PATRON_SLUG.test('Laptops')).toBe(false);
    });

    it('rechaza slugs con espacios', () => {
      expect(PATRON_SLUG.test('laptops pro')).toBe(false);
    });

    it('rechaza slugs con caracteres especiales', () => {
      expect(PATRON_SLUG.test('laptops_pro')).toBe(false);
      expect(PATRON_SLUG.test('laptops.pro')).toBe(false);
    });
  });

  describe('generarSlug', () => {
    it('convierte a minúsculas', () => {
      expect(generarSlug('Laptops')).toBe('laptops');
    });

    it('reemplaza espacios por guiones', () => {
      expect(generarSlug('Monitores Pro')).toBe('monitores-pro');
    });

    it('elimina acentos', () => {
      expect(generarSlug('Celulares')).toBe('celulares');
      expect(generarSlug('Electrónica')).toBe('electronica');
    });

    it('elimina caracteres especiales', () => {
      expect(generarSlug('Laptops & PCs')).toBe('laptops-pcs');
    });

    it('elimina guiones al inicio y final', () => {
      expect(generarSlug('  Laptops  ')).toBe('laptops');
    });
  });
});
