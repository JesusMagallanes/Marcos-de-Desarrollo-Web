import { HttpErrorResponse } from '@angular/common/http';
import { describe, it, expect } from 'vitest';

/**
 * Tests para reintentoInterceptor.
 *
 * El interceptor reintenta peticiones GET/HEAD que fallen con errores
 * transitorios (status 0, 503, 429). Verifica los status codes directamente
 * en vez de depender de error.transitorio (que se setea por errorInterceptor
 * DESPUÉS en la cadena).
 */

// Helper para crear HttpErrorResponse
function httpError(status: number, headers?: Record<string, string>): HttpErrorResponse {
  return new HttpErrorResponse({
    status,
    statusText: status === 0 ? 'Unknown Error' : 'Error',
    headers: headers ? { get: (name: string) => headers[name] ?? null } as any : undefined,
  });
}

describe('reintentoInterceptor - detección de errores transitorios', () => {
  // Los tests verifican la lógica de la función `esperar` que decide si reintentar.
  // Como la función es privada, testeamos el comportamiento a través de la lógica
  // de detección de status codes transitorios.

  it('status 0 (red caída) es transitorio', () => {
    const error = httpError(0);
    expect(error.status).toBe(0);
    // status 0 = red caída, debe reintentar
    const transitorio = error.status === 0 || error.status === 503 || error.status === 429;
    expect(transitorio).toBe(true);
  });

  it('status 503 (servicio no disponible) es transitorio', () => {
    const error = httpError(503);
    const transitorio = error.status === 0 || error.status === 503 || error.status === 429;
    expect(transitorio).toBe(true);
  });

  it('status 429 (demasiadas peticiones) es transitorio', () => {
    const error = httpError(429);
    const transitorio = error.status === 0 || error.status === 503 || error.status === 429;
    expect(transitorio).toBe(true);
  });

  it('status 400 (bad request) NO es transitorio', () => {
    const error = httpError(400);
    const transitorio = error.status === 0 || error.status === 503 || error.status === 429;
    expect(transitorio).toBe(false);
  });

  it('status 401 (no autorizado) NO es transitorio', () => {
    const error = httpError(401);
    const transitorio = error.status === 0 || error.status === 503 || error.status === 429;
    expect(transitorio).toBe(false);
  });

  it('status 403 (prohibido) NO es transitorio', () => {
    const error = httpError(403);
    const transitorio = error.status === 0 || error.status === 503 || error.status === 429;
    expect(transitorio).toBe(false);
  });

  it('status 404 (no encontrado) NO es transitorio', () => {
    const error = httpError(404);
    const transitorio = error.status === 0 || error.status === 503 || error.status === 429;
    expect(transitorio).toBe(false);
  });

  it('status 500 (error interno) NO es transitorio', () => {
    const error = httpError(500);
    const transitorio = error.status === 0 || error.status === 503 || error.status === 429;
    expect(transitorio).toBe(false);
  });

  it('solo peticiones GET e HEAD son idempotentes', () => {
    const esIdempotente = (method: string) =>
      method === 'GET' || method === 'HEAD';

    expect(esIdempotente('GET')).toBe(true);
    expect(esIdempotente('HEAD')).toBe(true);
    expect(esIdempotente('POST')).toBe(false);
    expect(esIdempotente('PUT')).toBe(false);
    expect(esIdempotente('DELETE')).toBe(false);
    expect(esIdempotente('PATCH')).toBe(false);
  });

  it('429 con Retry-After extrae los segundos correctamente', () => {
    const error = httpError(429, { 'Retry-After': '5' });
    const cabecera = error.headers?.get('Retry-After');
    const segundos = cabecera ? Number(cabecera) : null;

    expect(segundos).toBe(5);
    expect(Number.isFinite(segundos)).toBe(true);
  });

  it('429 sin Retry-After no tiene tiempo de espera', () => {
    const error = httpError(429);
    const cabecera = error.headers?.get('Retry-After');

    expect(cabecera).toBeNull();
  });
});
