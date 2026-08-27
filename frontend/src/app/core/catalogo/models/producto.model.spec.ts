import { describe, it, expect } from 'vitest';
import { imagenDe, sinStock, etiquetaDescuento, porcentajeDescuento, IMAGEN_POR_DEFECTO, Producto } from './producto.model';

describe('producto.model', () => {
  describe('imagenDe', () => {
    it('devuelve la imageUrl si existe', () => {
      expect(imagenDe({ imageUrl: 'https://img.example/1.jpg' })).toBe('https://img.example/1.jpg');
    });

    it('devuelve la imagen por defecto si imageUrl es null', () => {
      expect(imagenDe({ imageUrl: null })).toBe(IMAGEN_POR_DEFECTO);
    });

    it('devuelve la imagen por defecto si imageUrl es vacío', () => {
      expect(imagenDe({ imageUrl: '' })).toBe(IMAGEN_POR_DEFECTO);
    });

    it('devuelve la imagen por defecto si imageUrl es solo espacios', () => {
      expect(imagenDe({ imageUrl: '   ' })).toBe(IMAGEN_POR_DEFECTO);
    });
  });

  describe('sinStock', () => {
    it('devuelve true si stock es 0', () => {
      expect(sinStock({ stock: 0 })).toBe(true);
    });

    it('devuelve true si stock es negativo', () => {
      expect(sinStock({ stock: -1 })).toBe(true);
    });

    it('devuelve false si stock es positivo', () => {
      expect(sinStock({ stock: 5 })).toBe(false);
    });
  });

  describe('etiquetaDescuento', () => {
    const base: Pick<Producto, 'enOferta' | 'descuentoValor' | 'descuentoTipo'> = {
      enOferta: true,
      descuentoValor: 15,
      descuentoTipo: 'PORCENTAJE',
    };

    it('devuelve porcentaje para tipo PORCENTAJE', () => {
      expect(etiquetaDescuento(base as Producto)).toBe('-15%');
    });

    it('devuelve monto para tipo MONTO', () => {
      expect(etiquetaDescuento({ ...base, descuentoTipo: 'MONTO', descuentoValor: 20 } as Producto)).toBe('-S/ 20');
    });

    it('devuelve vacío si no está en oferta', () => {
      expect(etiquetaDescuento({ ...base, enOferta: false } as Producto)).toBe('');
    });

    it('devuelve vacío si descuentoValor es null', () => {
      expect(etiquetaDescuento({ ...base, descuentoValor: null } as Producto)).toBe('');
    });
  });

  describe('porcentajeDescuento', () => {
    it('calcula el porcentaje correctamente', () => {
      const p = { precio: 100, precioActual: 80, enOferta: true };
      expect(porcentajeDescuento(p)).toBe(20);
    });

    it('devuelve 0 si no está en oferta', () => {
      expect(porcentajeDescuento({ precio: 100, precioActual: 80, enOferta: false })).toBe(0);
    });

    it('devuelve 0 si precio es 0', () => {
      expect(porcentajeDescuento({ precio: 0, precioActual: 0, enOferta: true })).toBe(0);
    });

    it('redondea al entero más cercano', () => {
      const p = { precio: 99.99, precioActual: 85.50, enOferta: true };
      expect(porcentajeDescuento(p)).toBe(14);
    });
  });
});
