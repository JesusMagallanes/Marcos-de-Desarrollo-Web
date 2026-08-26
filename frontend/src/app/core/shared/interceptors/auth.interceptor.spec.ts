import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { authInterceptor } from './auth.interceptor';
import { errorInterceptor } from './error.interceptor';

/**
 * Lo que se prueba aquí es el sintoma que veia el usuario: una linea suelta que
 * decia "Necesitas iniciar sesion para esta operacion" **estando logueado**.
 *
 * <p>Ese texto lo redacta el backend y como respuesta HTTP es correcto, pero no
 * es algo que deba pintarse en la pagina: al usuario no le sirve leerlo, porque
 * ya se le esta llevando al login. La regla es que un 401 nunca llegue al
 * componente: o se renueva la sesion y se reintenta en silencio, o se redirige.
 */
/**
 * El jsdom de este proyecto no expone un localStorage completo (falta `clear`),
 * asi que se sustituye por uno en memoria. Mismo apanio que en auth.service.spec.
 */
function instalarAlmacenamiento(): void {
  const datos = new Map<string, string>();
  vi.stubGlobal('localStorage', {
    get length() {
      return datos.size;
    },
    clear: () => datos.clear(),
    getItem: (k: string) => datos.get(k) ?? null,
    key: (i: number) => [...datos.keys()][i] ?? null,
    removeItem: (k: string) => void datos.delete(k),
    setItem: (k: string, v: string) => void datos.set(k, String(v)),
  } as Storage);
}

describe('authInterceptor', () => {
  let http: HttpClient;
  let control: HttpTestingController;
  let navegar: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    instalarAlmacenamiento();
    navegar = vi.fn();

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor, authInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: { navigate: navegar } },
      ],
    });

    http = TestBed.inject(HttpClient);
    control = TestBed.inject(HttpTestingController);
  });

  it('adjunta el token a la peticion', () => {
    localStorage.setItem('sz_token', 'token-valido');

    http.get('/api/productos/mios').subscribe();

    const req = control.expectOne('/api/productos/mios');
    expect(req.request.headers.get('Authorization')).toBe('Bearer token-valido');
    req.flush([]);
  });

  it('renueva la sesion y reintenta sin que el componente se entere', () => {
    localStorage.setItem('sz_token', 'caducado');
    localStorage.setItem('sz_refresh', 'refresco-valido');

    let recibido: unknown = null;
    let hubo_error = false;
    http.get('/api/colaboradores/solicitudes/mia').subscribe({
      next: (r) => (recibido = r),
      error: () => (hubo_error = true),
    });

    // 1) La original caduca.
    control.expectOne('/api/colaboradores/solicitudes/mia').flush(
      { detail: 'Necesitas iniciar sesión para esta operación' },
      { status: 401, statusText: 'Unauthorized' },
    );

    // 2) Se canjea el refresh.
    control.expectOne('/api/auth/refresh').flush({
      accessToken: 'token-nuevo',
      refreshToken: 'refresco-nuevo',
      usuario: { id: 1, name: 'Ana', rol: 'CLIENTE', permisos: [] },
    });

    // 3) Y se repite con el token nuevo.
    const reintento = control.expectOne('/api/colaboradores/solicitudes/mia');
    expect(reintento.request.headers.get('Authorization')).toBe('Bearer token-nuevo');
    reintento.flush({ estado: 'PENDIENTE' });

    expect(hubo_error).toBe(false);
    expect(recibido).toEqual({ estado: 'PENDIENTE' });
  });

  it('si no hay refresh token, redirige y NO emite error', () => {
    localStorage.setItem('sz_token', 'caducado');

    let hubo_error = false;
    let completo = false;
    http.get('/api/carrito').subscribe({
      error: () => (hubo_error = true),
      complete: () => (completo = true),
    });

    control.expectOne('/api/carrito').flush(
      { detail: 'Necesitas iniciar sesión para esta operación' },
      { status: 401, statusText: 'Unauthorized' },
    );

    // ESTO es lo que arregla el sintoma: sin error, el componente se queda en su
    // estado de carga --con la animacion del logo-- hasta que cambia la ruta, en
    // vez de pintar el mensaje del backend como una linea suelta.
    expect(hubo_error).toBe(false);
    expect(completo).toBe(true);
    expect(navegar).toHaveBeenCalledWith(['/login'], { queryParams: { expirado: true } });
  });

  it('si el refresco tambien falla, redirige y tampoco emite error', () => {
    localStorage.setItem('sz_token', 'caducado');
    localStorage.setItem('sz_refresh', 'refresco-tambien-caducado');

    let hubo_error = false;
    http.get('/api/pedidos/mios').subscribe({ error: () => (hubo_error = true) });

    control.expectOne('/api/pedidos/mios').flush({}, { status: 401, statusText: 'Unauthorized' });
    control.expectOne('/api/auth/refresh').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(hubo_error).toBe(false);
    expect(navegar).toHaveBeenCalledWith(['/login'], { queryParams: { expirado: true } });
  });

  it('en el login, un 401 SI llega: son credenciales malas, no sesion caducada', () => {
    let hubo_error = false;
    http.post('/api/auth/login', {}).subscribe({ error: () => (hubo_error = true) });

    control.expectOne('/api/auth/login').flush(
      { detail: 'Correo o contraseña incorrectos' },
      { status: 401, statusText: 'Unauthorized' },
    );

    // Aqui el mensaje SI hay que enseniarlo: es lo unico que le dice al usuario
    // que se equivoco de contrasenia.
    expect(hubo_error).toBe(true);
    expect(navegar).not.toHaveBeenCalled();
  });
});
