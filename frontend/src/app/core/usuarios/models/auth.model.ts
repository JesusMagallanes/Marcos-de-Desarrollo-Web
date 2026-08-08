import { Rol } from './rol.model';
import { Usuario } from './usuario.model';

export interface LoginRequest {
  email: string;
  password: string;
}

/** Registro público: no acepta `rol`. Todo el que se registra aquí es CLIENTE. */
export interface RegistroRequest {
  name: string;
  lastname: string;
  emailAddress: string;
  password: string;
  phoneNumber: string;
  address: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  rol: Rol;
  /** Segundos de validez del access token. */
  expiraEn: number;
  usuario: Usuario;
}

/** Proveedor OAuth que el backend tiene realmente configurado. */
export interface ProveedorDisponible {
  id: 'google' | 'facebook';
  nombre: string;
  /** URL absoluta de inicio del flujo; requiere navegación de página completa. */
  url: string;
}

/** Requisitos de contraseña, iguales que los del backend. */
export const PATRON_PASSWORD = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&]).{8,}$/;

export const MENSAJE_PASSWORD =
  'Mínimo 8 caracteres, con mayúscula, minúscula, número y símbolo.';

/** El backend valida exactamente 9 dígitos. */
export const PATRON_TELEFONO = /^\d{9}$/;
