import { API } from '../shared/config/api.base';

/** Rutas del servicio `catalogo` (:8081). */
export const RUTAS_CATALOGO = {
  productos: {
    base: `${API}/productos`,
    porId: (id: number) => `${API}/productos/${id}`,
    porCategoria: (slug: string) => `${API}/productos/categoria/${slug}`,
    descuento: `${API}/productos/descuento`,
    descuentoLimpiar: `${API}/productos/descuento/limpiar`,
    valoraciones: (productoId: number) => `${API}/productos/${productoId}/valoraciones`,
    valoracionesMia: (productoId: number) => `${API}/productos/${productoId}/valoraciones/mia`,
  },

  categorias: {
    base: `${API}/categorias`,
    porId: (id: number) => `${API}/categorias/${id}`,
    porSlug: (slug: string) => `${API}/categorias/slug/${slug}`,
  },

  marcas: {
    base: `${API}/marcas`,
    porId: (id: number) => `${API}/marcas/${id}`,
    porCategoria: (categoriaId: number) => `${API}/marcas/categoria/${categoriaId}`,
  },

  guias: {
    base: `${API}/guias`,
    porSlug: (slug: string) => `${API}/guias/${slug}`,
    porId: (id: number) => `${API}/guias/${id}`,
    todas: `${API}/guias/admin/todas`,
    adminPorSlug: (slug: string) => `${API}/guias/admin/${slug}`,
  },

  chatbot: {
    mensaje: `${API}/chatbot/mensaje`,
  },
} as const;
