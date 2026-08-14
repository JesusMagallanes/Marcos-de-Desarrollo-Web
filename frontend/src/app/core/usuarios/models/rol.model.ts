/** Conceptos del servicio `usuarios`: roles dinámicos y sus permisos. */

/** Nombre del rol. Viaja dentro del JWT como claim `rol`. */
export type Rol = string;

/** Clasificación del rol: a qué tipo de cuenta se asigna. */
export type TipoRol = 'TRABAJADOR' | 'CLIENTE';

/** Origen de la cuenta: determina si admite login con contraseña. */
export type Proveedor = 'LOCAL' | 'GOOGLE' | 'FACEBOOK';

/** Permiso tal como lo expone el catálogo del backend. */
export interface PermisoInfo {
  codigo: string;
  descripcion: string;
  modulo: string;
}

/** Rol tal como lo devuelve el backend. */
export interface RolResponse {
  nombre: string;
  descripcion: string;
  tipo: TipoRol;
  /** Rol de sistema: no se puede borrar ni cambiar de tipo. */
  sistema: boolean;
  permisos: string[];
  /** Cuántos usuarios tienen este rol asignado. */
  usuarios: number;
}

/** Alta de rol desde el panel. */
export interface RolCreate {
  nombre: string;
  descripcion: string;
  tipo: TipoRol;
  permisos: string[];
}

/** Edición de rol: el nombre es la clave y no se puede cambiar. */
export interface RolUpdate {
  descripcion: string;
  tipo: TipoRol;
  permisos: string[];
}

/** Clase de Bootstrap por tipo de rol, para las etiquetas. */
export function clasePorTipo(tipo: TipoRol): string {
  return tipo === 'TRABAJADOR' ? 'bg-primary' : 'bg-secondary';
}

/** Etiqueta corta del tipo, para listados. */
export function etiquetaTipo(tipo: TipoRol): string {
  return tipo === 'TRABAJADOR' ? 'Personal' : 'Clientes';
}
