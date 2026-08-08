import { describe, expect, it } from 'vitest';
import {
  accionSiguiente,
  CLASE_ESTADO_PEDIDO,
  ETIQUETA_ESTADO_PEDIDO,
  EstadoPedido,
  esEstadoFinal,
  puedePasarA,
  siguienteEstado,
} from './pedido.model';

/**
 * Estas transiciones replican las que valida el backend. Si se desincronizan,
 * la interfaz ofrecería botones que acaban en un 409.
 */
describe('máquina de estados del pedido', () => {
  it('un pedido pendiente solo puede pagarse o cancelarse', () => {
    expect(puedePasarA('PENDIENTE', 'PAGADO')).toBe(true);
    expect(puedePasarA('PENDIENTE', 'CANCELADO')).toBe(true);
    expect(puedePasarA('PENDIENTE', 'EN_TRANSITO')).toBe(false);
    expect(puedePasarA('PENDIENTE', 'ENTREGADO')).toBe(false);
  });

  it('no se puede saltar del pago a la entrega sin pasar por el envío', () => {
    expect(puedePasarA('PAGADO', 'EN_TRANSITO')).toBe(true);
    expect(puedePasarA('PAGADO', 'ENTREGADO')).toBe(false);
    expect(puedePasarA('PAGADO', 'PENDIENTE')).toBe(false);
  });

  it('entregado y cancelado son estados finales', () => {
    const todos: EstadoPedido[] = [
      'PENDIENTE',
      'PAGADO',
      'EN_TRANSITO',
      'ENTREGADO',
      'CANCELADO',
    ];

    for (const destino of todos) {
      expect(puedePasarA('ENTREGADO', destino)).toBe(false);
      expect(puedePasarA('CANCELADO', destino)).toBe(false);
    }

    expect(esEstadoFinal('ENTREGADO')).toBe(true);
    expect(esEstadoFinal('CANCELADO')).toBe(true);
    expect(esEstadoFinal('PAGADO')).toBe(false);
  });

  it('siguienteEstado() propone el paso natural del flujo', () => {
    // Un PENDIENTE pasa a PAGADO: el backend rechaza PENDIENTE -> EN_TRANSITO.
    expect(siguienteEstado('PENDIENTE')).toBe('PAGADO');
    expect(siguienteEstado('PAGADO')).toBe('EN_TRANSITO');
    expect(siguienteEstado('EN_TRANSITO')).toBe('ENTREGADO');
    expect(siguienteEstado('ENTREGADO')).toBeNull();
    expect(siguienteEstado('CANCELADO')).toBeNull();
  });

  it('cada paso propuesto tiene una etiqueta para el botón', () => {
    expect(accionSiguiente('PENDIENTE')).toBe('Marcar como pagado');
    expect(accionSiguiente('PAGADO')).toBe('Marcar en camino');
    expect(accionSiguiente('EN_TRANSITO')).toBe('Marcar entregado');
    expect(accionSiguiente('ENTREGADO')).toBeNull();
  });

  it('lo que propone siguienteEstado() siempre es una transición válida', () => {
    const todos: EstadoPedido[] = ['PENDIENTE', 'PAGADO', 'EN_TRANSITO'];

    for (const actual of todos) {
      const siguiente = siguienteEstado(actual);
      expect(siguiente).not.toBeNull();
      expect(puedePasarA(actual, siguiente!)).toBe(true);
    }
  });

  it('todos los estados tienen etiqueta y clase para pintar', () => {
    const todos: EstadoPedido[] = [
      'PENDIENTE',
      'PAGADO',
      'EN_TRANSITO',
      'ENTREGADO',
      'CANCELADO',
    ];

    for (const estado of todos) {
      expect(ETIQUETA_ESTADO_PEDIDO[estado]).toBeTruthy();
      expect(CLASE_ESTADO_PEDIDO[estado]).toBeTruthy();
    }
  });
});
