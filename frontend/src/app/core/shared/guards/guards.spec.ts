import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { AuthService } from '../../usuarios/services/auth.service';
import { adminGuard } from './admin.guard';
import { authGuard } from './auth.guard';
import { invitadoGuard } from './invitado.guard';
import { staffGuard } from './staff.guard';

/** Doble de AuthService con solo los signals que consultan los guards. */
function authFalso(opciones: { autenticado?: boolean; admin?: boolean; staff?: boolean } = {}) {
  return {
    autenticado: signal(opciones.autenticado ?? false),
    esAdmin: signal(opciones.admin ?? false),
    esStaff: signal(opciones.staff ?? false),
  };
}

function ejecutar(guard: typeof authGuard, auth: ReturnType<typeof authFalso>, url = '/carrito') {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
  });
  return TestBed.runInInjectionContext(() =>
    guard({} as never, { url } as never),
  ) as boolean | UrlTree;
}

function destino(resultado: boolean | UrlTree): string {
  return resultado instanceof UrlTree ? resultado.toString() : '';
}

describe('authGuard', () => {
  it('deja pasar con sesión', () => {
    expect(ejecutar(authGuard, authFalso({ autenticado: true }))).toBe(true);
  });

  it('sin sesión redirige al login conservando el destino', () => {
    const r = ejecutar(authGuard, authFalso(), '/carrito');

    expect(r).toBeInstanceOf(UrlTree);
    expect(destino(r)).toContain('/login');
    expect(destino(r)).toContain('redirigir=%2Fcarrito');
  });
});

describe('adminGuard', () => {
  it('deja pasar a un administrador', () => {
    expect(ejecutar(adminGuard, authFalso({ autenticado: true, admin: true, staff: true })))
      .toBe(true);
  });

  it('a un usuario identificado sin permiso lo manda a la portada, no al login', () => {
    const r = ejecutar(adminGuard, authFalso({ autenticado: true }), '/admin');

    expect(destino(r)).toBe('/');
  });

  it('a un anónimo lo manda al login', () => {
    const r = ejecutar(adminGuard, authFalso(), '/admin');

    expect(destino(r)).toContain('/login');
  });
});

describe('staffGuard', () => {
  it('deja pasar a un empleado', () => {
    expect(ejecutar(staffGuard, authFalso({ autenticado: true, staff: true }))).toBe(true);
  });

  it('bloquea a un cliente', () => {
    const r = ejecutar(staffGuard, authFalso({ autenticado: true }), '/envios');
    expect(destino(r)).toBe('/');
  });
});

describe('invitadoGuard', () => {
  it('deja ver el login a quien no tiene sesión', () => {
    expect(ejecutar(invitadoGuard, authFalso(), '/login')).toBe(true);
  });

  it('a quien ya inició sesión lo saca del login', () => {
    const r = ejecutar(invitadoGuard, authFalso({ autenticado: true }), '/login');
    expect(destino(r)).toBe('/');
  });
});
