import { Proveedor } from './rol.model';

/**
 * La dirección de entrega guardada en el perfil.
 *
 * NO lleva el nombre de quien recibe ni el teléfono: el perfil ya los tiene.
 * Se usan como valor de partida al pagar, y ahí sí se pueden cambiar —un regalo
 * va a nombre de otro— pero duplicarlos aquí solo garantiza que algún día digan
 * cosas distintas.
 */
export interface DireccionUsuario {
  calle: string;
  numero: string;
  referencia?: string | null;
  codigoPostal: string;
  distrito: string;
  provincia: string;
  departamento: string;
  pais?: string | null;
  latitud?: number | null;
  longitud?: number | null;
}

/** Usuario tal como lo devuelve el backend. Nunca incluye el hash de la contraseña. */
export interface Usuario {
  id: number;
  name: string;
  lastname: string;
  emailAddress: string;
  /** Nulos en cuentas de Google/Facebook hasta que el usuario los complete. */
  phoneNumber: string | null;
  address: string | null;
  /** `null` mientras no haya puesto ninguna: sin ella no se puede comprar. */
  direccion: DireccionUsuario | null;
  rol: string;
  proveedor: Proveedor;
  /** Permisos que el backend resuelve a partir del rol. */
  permisos: string[];
}

/** Actualización de perfil. */
export interface PerfilUpdate {
  name: string;
  lastname: string;
  emailAddress: string;
  phoneNumber: string;
}

/** Alta desde el panel admin: es el único sitio donde se puede fijar el rol. */
export interface UsuarioCreate {
  name: string;
  lastname: string;
  emailAddress: string;
  password: string;
  phoneNumber: string;
  address: string;
  rol: string;
}

/** Cuerpo de `PATCH /api/usuarios/{id}/rol`. */
export interface CambioRol {
  rol: string;
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
  return !!usuario && (!usuario.phoneNumber || !usuario.direccion);
}

export function esCuentaSocial(usuario: Usuario | null): boolean {
  return !!usuario && usuario.proveedor !== 'LOCAL';
}
