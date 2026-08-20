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

  /** Catalogo publico: departamentos, provincias y distritos del Peru. */
  ubigeo: {
    departamentos: `${API}/ubigeo/departamentos`,
    provincias: `${API}/ubigeo/provincias`,
    distritos: `${API}/ubigeo/distritos`,
  },
  usuarios: {
    base: `${API}/usuarios`,
    porId: (id: number) => `${API}/usuarios/${id}`,
    perfil: (id: number) => `${API}/usuarios/${id}/perfil`,
    direccion: (id: number) => `${API}/usuarios/${id}/direccion`,
    rol: (id: number) => `${API}/usuarios/${id}/rol`,
  },

  colaboradores: {
    solicitudes: `${API}/colaboradores/solicitudes`,
    mia: `${API}/colaboradores/solicitudes/mia`,
    adjuntos: `${API}/colaboradores/solicitudes/adjuntos`,
    adjunto: (id: number) => `${API}/colaboradores/solicitudes/adjuntos/${id}`,
    aprobar: (id: number) => `${API}/colaboradores/solicitudes/${id}/aprobar`,
    rechazar: (id: number) => `${API}/colaboradores/solicitudes/${id}/rechazar`,
  },

  roles: {
    base: `${API}/roles`,
    porNombre: (nombre: string) => `${API}/roles/${encodeURIComponent(nombre)}`,
    permisos: `${API}/roles/permisos`,
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
