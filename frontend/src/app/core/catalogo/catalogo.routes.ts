import { API } from '../shared/config/api.base';

/** Rutas del servicio `catalogo` (:8081). */
export const RUTAS_CATALOGO = {
  productos: {
    base: `${API}/productos`,
    porId: (id: number) => `${API}/productos/${id}`,
    porCategoria: (slug: string) => `${API}/productos/categoria/${slug}`,
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

  chatbot: {
    mensaje: `${API}/chatbot/mensaje`,
  },
} as const;
