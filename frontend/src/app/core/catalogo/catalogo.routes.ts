import { API } from '../shared/config/api.base';

/** Rutas del servicio `catalogo` (:8081). */
export const RUTAS_CATALOGO = {
  productos: {
    base: `${API}/productos`,
    /** Las tres listas de la portada en una respuesta, ya acotadas. */
    portada: `${API}/productos/portada`,
    porId: (id: number) => `${API}/productos/${id}`,
    porCategoria: (slug: string) => `${API}/productos/categoria/${slug}`,
    // Productos de colaborador y su cola de revisión (SZ-B08).
    mios: `${API}/productos/mios`,
    mio: (id: number) => `${API}/productos/mios/${id}`,
    moderacion: `${API}/productos/moderacion`,
    aprobarProducto: (id: number) => `${API}/productos/moderacion/${id}/aprobar`,
    rechazarProducto: (id: number) => `${API}/productos/moderacion/${id}/rechazar`,

    /** Listado del panel de descuentos: filtros y conteos en el servidor. */
    paraDescuentos: `${API}/productos/descuentos`,
    descuento: `${API}/productos/descuento`,
    descuentoLimpiar: `${API}/productos/descuento/limpiar`,
    valoraciones: (productoId: number) => `${API}/productos/${productoId}/valoraciones`,
    valoracionesMia: (productoId: number) => `${API}/productos/${productoId}/valoraciones/mia`,
  },

  valoracionesAdmin: {
    base: `${API}/valoraciones/admin`,
    cambiarEstado: (id: number) => `${API}/valoraciones/admin/${id}/estado`,
    porId: (id: number) => `${API}/valoraciones/admin/${id}`,
  },

  valoraciones: {
    /** Las 6 aprobadas mejor valoradas (más estrellas), para la portada. */
    top: `${API}/valoraciones/top`,
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

  /** Confirmación de operaciones escritas offline (idempotente por operacionId). */
  sync: {
    valoraciones: `${API}/sync/valoraciones`,
  },
} as const;
