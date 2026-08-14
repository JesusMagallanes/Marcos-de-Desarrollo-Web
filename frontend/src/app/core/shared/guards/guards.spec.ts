import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { AuthService } from '../../usuarios/services/auth.service';
import { adminGuard } from './admin.guard';
import { authGuard } from './auth.guard';
import { invitadoGuard } from './invitado.guard';
import { permisoGuard } from './permiso.guard';
import { staffGuard } from './staff.guard';

/** Doble de AuthService con solo los signals que consultan los guards. */
function authFalso(
  opciones: {
    autenticado?: boolean;
    admin?: boolean;
    adminPanel?: boolean;
    staff?: boolean;
    permisos?: string[];
  } = {},
) {
  const permisos = opciones.permisos ?? [];
  return {
    autenticado: signal(opciones.autenticado ?? false),
    esAdmin: signal(opciones.admin ?? false),
    esAdminPanel: signal(
      (opciones.adminPanel ?? false) || permisos.some((p) => p.endsWith('_GESTIONAR')),
    ),
    esStaff: signal(
      (opciones.staff ?? false) ||
        permisos.includes('PEDIDOS_GESTIONAR') ||
        permisos.includes('ENVIOS_GESTIONAR'),
    ),
    tienePermiso: (p: string) => permisos.includes(p) || (opciones.admin ?? false),
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
    expect(
      ejecutar(adminGuard, authFalso({ autenticado: true, admin: true, adminPanel: true, staff: true })),
    ).toBe(true);
  });

  it('deja pasar a un rol dinámico con algún permiso de gestión', () => {
    expect(
      ejecutar(
        adminGuard,
        authFalso({ autenticado: true, adminPanel: true, permisos: ['GUIAS_GESTIONAR'] }),
      ),
    ).toBe(true);
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

  it('deja pasar a un rol dinámico con permiso de pedidos', () => {
    expect(
      ejecutar(
        staffGuard,
        authFalso({ autenticado: true, permisos: ['PEDIDOS_GESTIONAR'] }),
      ),
    ).toBe(true);
  });

  it('bloquea a un cliente', () => {
    const r = ejecutar(staffGuard, authFalso({ autenticado: true }), '/envios');
    expect(destino(r)).toBe('/');
  });
});

describe('permisoGuard', () => {
  it('deja pasar si el usuario tiene el permiso', () => {
    expect(
      ejecutar(
        permisoGuard('PRODUCTOS_GESTIONAR'),
        authFalso({ autenticado: true, permisos: ['PRODUCTOS_GESTIONAR'] }),
        '/admin/productos',
      ),
    ).toBe(true);
  });

  it('deja pasar a un ADMINISTRADOR aunque el permiso no figure en el token', () => {
    expect(
      ejecutar(
        permisoGuard('PRODUCTOS_GESTIONAR'),
        authFalso({ autenticado: true, admin: true }),
        '/admin/productos',
      ),
    ).toBe(true);
  });

  it('a un identificado sin el permiso lo manda a la portada', () => {
    const r = ejecutar(
      permisoGuard('PRODUCTOS_GESTIONAR'),
      authFalso({ autenticado: true }),
      '/admin/productos',
    );
    expect(destino(r)).toBe('/');
  });

  it('a un anónimo lo manda al login', () => {
    const r = ejecutar(
      permisoGuard('PRODUCTOS_GESTIONAR'),
      authFalso(),
      '/admin/productos',
    );
    expect(destino(r)).toContain('/login');
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

