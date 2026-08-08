import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ALMACENAMIENTO } from '../../shared/config/constantes';
import { AuthResponse, Usuario } from '../models';
import { AuthService } from './auth.service';

/**
 * El jsdom de este proyecto no expone un localStorage completo (falta `clear`),
 * así que se sustituye por uno en memoria. Además aísla cada prueba.
 */
function instalarAlmacenamiento(): Storage {
  const datos = new Map<string, string>();
  const almacen: Storage = {
    get length() {
      return datos.size;
    },
    clear: () => datos.clear(),
    getItem: (k) => datos.get(k) ?? null,
    key: (i) => [...datos.keys()][i] ?? null,
    removeItem: (k) => void datos.delete(k),
    setItem: (k, v) => void datos.set(k, String(v)),
  };
  vi.stubGlobal('localStorage', almacen);
  return almacen;
}

function usuario(rol: Usuario['rol'] = 'CLIENTE'): Usuario {
  return {
    id: 7,
    name: 'Ana',
    lastname: 'Pérez',
    emailAddress: 'ana@ejemplo.com',
    phoneNumber: '987654321',
    address: 'Av. Lima 123',
    rol,
    proveedor: 'LOCAL',
  };
}

function respuesta(rol: Usuario['rol'] = 'CLIENTE'): AuthResponse {
  return {
    accessToken: 'token-acceso',
    refreshToken: 'token-refresco',
    rol,
    expiraEn: 3600,
    usuario: usuario(rol),
  };
}

describe('AuthService', () => {
  let servicio: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    instalarAlmacenamiento();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    servicio = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => vi.unstubAllGlobals());

  it('empieza sin sesión', () => {
    expect(servicio.autenticado()).toBe(false);
    expect(servicio.usuario()).toBeNull();
    expect(servicio.esAdmin()).toBe(false);
  });

  it('login() guarda tokens y usuario', () => {
    servicio.login({ email: 'ana@ejemplo.com', password: 'Secreta1!' }).subscribe();
    http.expectOne('/api/auth/login').flush(respuesta());

    expect(servicio.autenticado()).toBe(true);
    expect(servicio.token).toBe('token-acceso');
    expect(servicio.refreshToken).toBe('token-refresco');
    expect(localStorage.getItem(ALMACENAMIENTO.usuario)).toContain('ana@ejemplo.com');
  });

  it('distingue los roles', () => {
    servicio.login({ email: 'a@b.c', password: 'x' }).subscribe();
    http.expectOne('/api/auth/login').flush(respuesta('ADMINISTRADOR'));

    expect(servicio.esAdmin()).toBe(true);
    expect(servicio.esStaff()).toBe(true);
    expect(servicio.esEmpleado()).toBe(false);
  });

  it('un EMPLEADO es staff pero no admin', () => {
    servicio.login({ email: 'a@b.c', password: 'x' }).subscribe();
    http.expectOne('/api/auth/login').flush(respuesta('EMPLEADO'));

    expect(servicio.esAdmin()).toBe(false);
    expect(servicio.esStaff()).toBe(true);
  });

  it('refrescar() envía el token guardado y sustituye el par', () => {
    localStorage.setItem(ALMACENAMIENTO.refresh, 'refresco-viejo');

    servicio.refrescar().subscribe();

    const req = http.expectOne('/api/auth/refresh');
    expect(req.request.body).toEqual({ refreshToken: 'refresco-viejo' });
    req.flush({ ...respuesta(), accessToken: 'nuevo-acceso', refreshToken: 'nuevo-refresco' });

    expect(servicio.token).toBe('nuevo-acceso');
    expect(servicio.refreshToken).toBe('nuevo-refresco');
  });

  it('logout() manda el refresh al backend para que lo revoque', () => {
    localStorage.setItem(ALMACENAMIENTO.refresh, 'para-revocar');

    servicio.logout();

    const req = http.expectOne('/api/auth/logout');
    expect(req.request.body).toEqual({ refreshToken: 'para-revocar' });
    req.flush(null);

    expect(servicio.autenticado()).toBe(false);
    expect(servicio.token).toBeNull();
  });

  it('limpiarSesion() borra todo el almacenamiento', () => {
    servicio.login({ email: 'a@b.c', password: 'x' }).subscribe();
    http.expectOne('/api/auth/login').flush(respuesta());

    servicio.limpiarSesion();

    expect(localStorage.getItem(ALMACENAMIENTO.token)).toBeNull();
    expect(localStorage.getItem(ALMACENAMIENTO.refresh)).toBeNull();
    expect(localStorage.getItem(ALMACENAMIENTO.usuario)).toBeNull();
    expect(servicio.autenticado()).toBe(false);
  });

  it('rehidrata la sesión desde localStorage al recargar la página', () => {
    // La instancia del beforeEach ya leyó el almacenamiento vacío, así que
    // hay que sembrar primero y construir el servicio DESPUÉS.
    localStorage.setItem(ALMACENAMIENTO.usuario, JSON.stringify(usuario('ADMINISTRADOR')));

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const recargado = TestBed.inject(AuthService);

    expect(recargado.usuario()?.emailAddress).toBe('ana@ejemplo.com');
    expect(recargado.esAdmin()).toBe(true);
  });

  it('un usuario guardado corrupto no rompe el arranque y se descarta', () => {
    localStorage.setItem(ALMACENAMIENTO.usuario, '{no es json');

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });

    const recargado = TestBed.inject(AuthService);

    expect(recargado.usuario()).toBeNull();
    expect(localStorage.getItem(ALMACENAMIENTO.usuario)).toBeNull();
  });

  it('detecta las cuentas sociales', () => {
    servicio.login({ email: 'a@b.c', password: 'x' }).subscribe();
    http.expectOne('/api/auth/login').flush({
      ...respuesta(),
      usuario: { ...usuario(), proveedor: 'GOOGLE' as const },
    });

    expect(servicio.esCuentaSocial()).toBe(true);
  });
});
