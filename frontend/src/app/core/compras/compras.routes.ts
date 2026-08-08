import { API } from '../shared/config/api.base';

/** Rutas del servicio `compras` (:8083). */
export const RUTAS_COMPRAS = {
  carrito: {
    base: `${API}/carrito`,
    items: `${API}/carrito/items`,
    item: (itemId: number) => `${API}/carrito/items/${itemId}`,
  },

  pedidos: {
    base: `${API}/pedidos`,
    mios: `${API}/pedidos/mios`,
    porId: (id: number) => `${API}/pedidos/${id}`,
    estado: (id: number) => `${API}/pedidos/${id}/estado`,
  },

  metodosPago: {
    base: `${API}/metodos-pago`,
    porId: (id: number) => `${API}/metodos-pago/${id}`,
  },

  envios: {
    base: `${API}/envios`,
    mios: `${API}/envios/mios`,
    estado: (id: number) => `${API}/envios/${id}/estado`,
  },

  pagos: {
    preferencia: `${API}/pagos/preferencia`,
    confirmar: `${API}/pagos/confirmar`,
  },
} as const;
