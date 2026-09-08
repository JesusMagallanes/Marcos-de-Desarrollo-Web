import { API } from '../shared/config/api.base';

/**
 * Rutas del módulo de descubrimiento (vive en `catalogo`, :8081).
 *
 * <p>Están aquí y no repartidas por los componentes por lo mismo que las demás:
 * un cambio de prefijo se hace en un sitio, y quien lee el módulo ve de un
 * vistazo toda su superficie de red.
 */
export const RUTAS_DESCUBRIMIENTO = {
  /** Ingesta por lotes. Nunca de una en una. */
  eventos: `${API}/descubrimiento/eventos`,
  /** Aparte de los eventos: el volumen es sesenta veces mayor. */
  impresiones: `${API}/descubrimiento/impresiones`,
  /** Todos los carruseles en una sola petición, ya de-duplicados. */
  home: `${API}/descubrimiento/home`,
  segunIntereses: `${API}/descubrimiento/segun-intereses`,
  similares: (itemId: number) => `${API}/descubrimiento/similares/${itemId}`,
  tendencias: `${API}/descubrimiento/tendencias`,
  /** Derechos de acceso y cancelación sobre el propio perfil. */
  misIntereses: `${API}/descubrimiento/mis-intereses`,
} as const;
