import { describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../models/api-error.model';
import { EstadoPeticion } from './estado-peticion';

function errorFalso(parcial: Partial<ErrorApi> = {}): ErrorApi {
  return {
    estado: 409,
    mensaje: 'Solo quedan 2 unidades',
    camposInvalidos: {},
    correlacionId: 'abc-123',
    reintentarEn: null,
    noAutenticado: false,
    sinPermiso: false,
    noEncontrado: false,
    conflicto: true,
    servicioCaido: false,
    entradaInvalida: false,
    limitado: false,
    transitorio: false,
    ...parcial,
  };
}

describe('EstadoPeticion', () => {
  it('arranca en reposo', () => {
    const estado = new EstadoPeticion();

    expect(estado.cargando()).toBe(false);
    expect(estado.hayError()).toBe(false);
    expect(estado.mensajeError()).toBe('');
    expect(estado.aviso()).toBe('');
  });

  it('iniciar() marca cargando y borra el error anterior', () => {
    const estado = new EstadoPeticion();
    estado.fallo(errorFalso());

    estado.iniciar();

    expect(estado.cargando()).toBe(true);
    expect(estado.hayError()).toBe(false);
  });

  it('exito() deja de cargar y no deja error', () => {
    const estado = new EstadoPeticion();
    estado.iniciar();

    estado.exito();

    expect(estado.cargando()).toBe(false);
    expect(estado.hayError()).toBe(false);
  });

  it('fallo() expone el mensaje que redactó el backend', () => {
    const estado = new EstadoPeticion();
    estado.iniciar();

    estado.fallo(errorFalso({ mensaje: 'Stock insuficiente de «Monitor LG»' }));

    expect(estado.cargando()).toBe(false);
    expect(estado.hayError()).toBe(true);
    expect(estado.mensajeError()).toBe('Stock insuficiente de «Monitor LG»');
    expect(estado.error()?.conflicto).toBe(true);
  });

  it('expone los errores por campo de un 400 de validación', () => {
    const estado = new EstadoPeticion();

    estado.fallo(
      errorFalso({
        estado: 400,
        conflicto: false,
        camposInvalidos: { password: 'no debe estar vacío' },
      }),
    );

    expect(estado.camposInvalidos()['password']).toBe('no debe estar vacío');
  });

  it('el aviso se oculta solo al pasar la duración', () => {
    vi.useFakeTimers();
    const estado = new EstadoPeticion(2800);

    estado.exito('Producto creado.');
    expect(estado.aviso()).toBe('Producto creado.');

    vi.advanceTimersByTime(2799);
    expect(estado.aviso()).toBe('Producto creado.');

    vi.advanceTimersByTime(1);
    expect(estado.aviso()).toBe('');

    vi.useRealTimers();
  });

  it('un aviso nuevo reinicia el temporizador del anterior', () => {
    vi.useFakeTimers();
    const estado = new EstadoPeticion(1000);

    estado.mostrarAviso('primero');
    vi.advanceTimersByTime(900);

    estado.mostrarAviso('segundo');
    vi.advanceTimersByTime(900);

    // Si no se hubiera reiniciado, ya estaría vacío.
    expect(estado.aviso()).toBe('segundo');

    vi.advanceTimersByTime(100);
    expect(estado.aviso()).toBe('');

    vi.useRealTimers();
  });

  it('destruir() cancela el temporizador pendiente', () => {
    vi.useFakeTimers();
    const estado = new EstadoPeticion(1000);

    estado.mostrarAviso('hola');
    estado.destruir();
    vi.advanceTimersByTime(2000);

    // No debe lanzar ni tocar signals de un componente ya destruido.
    expect(estado.aviso()).toBe('hola');

    vi.useRealTimers();
  });

  it('limpiarError() descarta el error sin tocar el aviso', () => {
    const estado = new EstadoPeticion();
    estado.mostrarAviso('guardado');
    estado.fallo(errorFalso());

    estado.limpiarError();

    expect(estado.hayError()).toBe(false);
    expect(estado.aviso()).toBe('guardado');
  });
});
