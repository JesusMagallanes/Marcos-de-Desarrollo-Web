/** Roles y origen de la cuenta. Son conceptos del servicio `usuarios`. */

/** Viaja dentro del JWT como claim `rol`. */
export type Rol = 'CLIENTE' | 'EMPLEADO' | 'ADMINISTRADOR';

/** Origen de la cuenta: determina si admite login con contraseña. */
export type Proveedor = 'LOCAL' | 'GOOGLE' | 'FACEBOOK';

export const ROLES: readonly Rol[] = ['CLIENTE', 'EMPLEADO', 'ADMINISTRADOR'] as const;

export const ETIQUETA_ROL: Record<Rol, string> = {
  CLIENTE: 'Cliente',
  EMPLEADO: 'Empleado',
  ADMINISTRADOR: 'Administrador',
};

/** Clase de Bootstrap por rol, para no repetir el switch en cada vista. */
export const CLASE_ROL: Record<Rol, string> = {
  CLIENTE: 'bg-secondary',
  EMPLEADO: 'bg-primary',
  ADMINISTRADOR: 'bg-danger',
};

export function esStaff(rol: Rol | null | undefined): boolean {
  return rol === 'ADMINISTRADOR' || rol === 'EMPLEADO';
}
