import { HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { expect, vi } from 'vitest';

/**
 * Espera a que aparezca una petición contra `url` en el controlador de
 * pruebas y la devuelve.
 *
 * <p>Con la caché offline-first, las lecturas consultan IndexedDB ANTES de
 * disparar el HTTP: la petición llega varios microtaskes después de la
 * suscripción y `expectOne` síncrono fallaría por llegar temprano. Esta ayuda
 * sondea con `vi.waitFor` hasta que exista.
 *
 * @param http controlador de peticiones del banco de pruebas
 * @param url URL completa tal como viaja (`/api/productos?page=0&size=24`)
 */
export async function esperarPeticion(http: HttpTestingController, url: string): Promise<TestRequest> {
  let encontradas: TestRequest[] = [];
  await vi.waitFor(() => {
    encontradas = http.match(url);
    expect(encontradas.length).toBeGreaterThan(0);
  });
  return encontradas[0];
}
