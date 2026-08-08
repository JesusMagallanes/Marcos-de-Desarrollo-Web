import { Proveedor, Rol } from './rol.model';

/** Usuario tal como lo devuelve el backend. Nunca incluye el hash de la contraseña. */
export interface Usuario {
  id: number;
  name: string;
  lastname: string;
  emailAddress: string;
  /** Nulos en cuentas de Google/Facebook hasta que el usuario los complete. */
  phoneNumber: string | null;
  address: string | null;
  rol: Rol;
  proveedor: Proveedor;
}

/** Actualización de perfil. */
export interface PerfilUpdate {
  name: string;
  lastname: string;
  emailAddress: string;
  phoneNumber: string;
  address: string;
}

/** Alta desde el panel admin: es el único sitio donde se puede fijar el rol. */
export interface UsuarioCreate {
  name: string;
  lastname: string;
  emailAddress: string;
  password: string;
  phoneNumber: string;
  address: string;
  rol: Rol;
}

/** Cuerpo de `PATCH /api/usuarios/{id}/rol`. */
export interface CambioRol {
  rol: Rol;
}

export function nombreCompleto(usuario: Usuario | null): string {
  return usuario ? `${usuario.name} ${usuario.lastname}`.trim() : '';
}

/** Iniciales para el avatar del perfil. */
export function iniciales(usuario: Usuario | null): string {
  if (!usuario) {
    return '';
  }
  return `${usuario.name?.[0] ?? ''}${usuario.lastname?.[0] ?? ''}`.toUpperCase();
}

/** Las cuentas sociales llegan sin teléfono ni dirección; el checkout los exige. */
export function perfilIncompleto(usuario: Usuario | null): boolean {
  return !!usuario && (!usuario.phoneNumber || !usuario.address);
}

export function esCuentaSocial(usuario: Usuario | null): boolean {
  return !!usuario && usuario.proveedor !== 'LOCAL';
}
