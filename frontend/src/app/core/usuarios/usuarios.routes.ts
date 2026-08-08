import { API } from '../shared/config/api.base';

/** Rutas del servicio `usuarios` (:8082). */
export const RUTAS_USUARIOS = {
  auth: {
    login: `${API}/auth/login`,
    registrar: `${API}/auth/registrar`,
    refresh: `${API}/auth/refresh`,
    logout: `${API}/auth/logout`,
    yo: `${API}/auth/yo`,
    proveedores: `${API}/auth/proveedores`,
  },

  usuarios: {
    base: `${API}/usuarios`,
    porId: (id: number) => `${API}/usuarios/${id}`,
    perfil: (id: number) => `${API}/usuarios/${id}/perfil`,
    rol: (id: number) => `${API}/usuarios/${id}/rol`,
  },
} as const;

/**
 * Rutas donde un 401 significa "credenciales incorrectas", no "sesión
 * caducada": el interceptor no debe intentar refrescar el token en ellas.
 */
export const RUTAS_SIN_REFRESCO: readonly string[] = [
  RUTAS_USUARIOS.auth.login,
  RUTAS_USUARIOS.auth.registrar,
  RUTAS_USUARIOS.auth.refresh,
] as const;
