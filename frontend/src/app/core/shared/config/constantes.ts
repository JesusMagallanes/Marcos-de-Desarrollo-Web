/** Valores fijos de la aplicación, para no repetirlos como literales sueltos. */

/** Claves de localStorage. Con prefijo para no chocar con otras apps del dominio. */
export const ALMACENAMIENTO = {
  token: 'sz_token',
  refresh: 'sz_refresh',
  usuario: 'sz_user',
} as const;

/** Cabecera de correlación; el backend la propaga entre los cuatro servicios. */
export const CABECERA_CORRELACION = 'X-Correlation-Id';

/** Reglas de envío. Duplican las del backend solo para poder mostrarlas. */
export const ENVIO = {
  umbralGratis: 200,
  costo: 15,
} as const;

/** Tamaños de página del catálogo. */
export const PAGINACION = {
  productosPorPagina: 12,
  relacionados: 6,
  destacadosPortada: 10,
} as const;

/** Milisegundos que permanece visible un aviso emergente. */
export const DURACION_AVISO = 2800;
