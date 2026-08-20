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
  /*
   * La lista sale del Record y no escrita a mano.
   *
   * Escrita a mano ya se quedó corta una vez: al añadir CONFIRMADO estas
   * pruebas siguieron pasando sin mirarlo, que es justo lo contrario de lo que
   * se les pide. `Record<EstadoPedido, …>` obliga al compilador a tenerlos
   * todos, así que un estado nuevo entra aquí solo.
   */
  const TODOS = Object.keys(ETIQUETA_ESTADO_PEDIDO) as EstadoPedido[];

  it('un pedido pendiente puede cobrarse, confirmarse o cancelarse', () => {
    expect(puedePasarA('PENDIENTE', 'PAGADO')).toBe(true);
    // La salida del checkout contra entrega.
    expect(puedePasarA('PENDIENTE', 'CONFIRMADO')).toBe(true);
    expect(puedePasarA('PENDIENTE', 'CANCELADO')).toBe(true);
    expect(puedePasarA('PENDIENTE', 'EN_TRANSITO')).toBe(false);
    expect(puedePasarA('PENDIENTE', 'ENTREGADO')).toBe(false);
  });

  it('un contra entrega se envía sin pasar por PAGADO: se cobra al entregar', () => {
    expect(puedePasarA('CONFIRMADO', 'EN_TRANSITO')).toBe(true);
    expect(puedePasarA('CONFIRMADO', 'CANCELADO')).toBe(true);
    expect(puedePasarA('CONFIRMADO', 'PAGADO')).toBe(false);
    expect(puedePasarA('CONFIRMADO', 'ENTREGADO')).toBe(false);
  });

  it('no se puede saltar del pago a la entrega sin pasar por el envío', () => {
    expect(puedePasarA('PAGADO', 'EN_TRANSITO')).toBe(true);
    expect(puedePasarA('PAGADO', 'ENTREGADO')).toBe(false);
    expect(puedePasarA('PAGADO', 'PENDIENTE')).toBe(false);
  });

  it('entregado y cancelado son estados finales', () => {
    for (const destino of TODOS) {
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
    // El contra entrega ya está en firme: lo siguiente es mandarlo.
    expect(siguienteEstado('CONFIRMADO')).toBe('EN_TRANSITO');
    expect(siguienteEstado('PAGADO')).toBe('EN_TRANSITO');
    expect(siguienteEstado('EN_TRANSITO')).toBe('ENTREGADO');
    expect(siguienteEstado('ENTREGADO')).toBeNull();
    expect(siguienteEstado('CANCELADO')).toBeNull();
  });

  it('cada paso propuesto tiene una etiqueta para el botón', () => {
    expect(accionSiguiente('PENDIENTE')).toBe('Marcar como pagado');
    expect(accionSiguiente('CONFIRMADO')).toBe('Marcar en camino');
    expect(accionSiguiente('PAGADO')).toBe('Marcar en camino');
    expect(accionSiguiente('EN_TRANSITO')).toBe('Marcar entregado');
    expect(accionSiguiente('ENTREGADO')).toBeNull();
  });

  it('lo que propone siguienteEstado() siempre es una transición válida', () => {
    // Recorre TODOS: si un estado nuevo propone un paso que el backend rechaza,
    // la interfaz pintaría un botón que solo sabe devolver un 409.
    for (const actual of TODOS) {
      const siguiente = siguienteEstado(actual);
      // Solo los finales se quedan sin paso siguiente.
      if (siguiente === null) {
        expect(esEstadoFinal(actual)).toBe(true);
        continue;
      }
      expect(puedePasarA(actual, siguiente)).toBe(true);
    }
  });

  it('todos los estados tienen etiqueta y clase para pintar', () => {
    for (const estado of TODOS) {
      expect(ETIQUETA_ESTADO_PEDIDO[estado]).toBeTruthy();
      expect(CLASE_ESTADO_PEDIDO[estado]).toBeTruthy();
    }
  });
});
