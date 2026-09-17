import { API } from '../shared/config/api.base';

/** Rutas del servicio `catalogo` (:8081). */
export const RUTAS_CATALOGO = {
  /** IA aplicada. Todo responde 204 cuando la IA no puede aportar. */
  ia: {
    estado: `${API}/ia/estado`,
    interpretar: `${API}/ia/busqueda/interpretar`,
    redactar: `${API}/ia/productos/redactar`,
    resumenResenas: (productoId: number) => `${API}/ia/productos/${productoId}/resumen-resenas`,
    moderacionProducto: (id: number) => `${API}/ia/moderacion/productos/${id}`,
    moderacionValoracion: (id: number) => `${API}/ia/moderacion/valoraciones/${id}`,
    busquedaFoto: `${API}/ia/busqueda/foto`,
    preguntar: (productoId: number) => `${API}/ia/productos/${productoId}/preguntar`,
    comparar: `${API}/ia/comparar`,
    complementos: `${API}/ia/carrito/complementos`,
    ayudaResena: `${API}/ia/resenas/ayuda`,
    revisarFoto: `${API}/ia/productos/revisar-foto`,
    comoCorregir: (id: number) => `${API}/ia/productos/mios/${id}/como-corregir`,
  },
  productos: {
    base: `${API}/productos`,
    /** Las tres listas de la portada en una respuesta, ya acotadas. */
    portada: `${API}/productos/portada`,
    porId: (id: number) => `${API}/productos/${id}`,
    porCategoria: (slug: string) => `${API}/productos/categoria/${slug}`,
    /** Facetas (marcas, atributos y conteos) del mismo conjunto filtrado. */
    facetas: `${API}/productos/facetas`,
    /** Fotos alojadas en la tienda: POST para subir; las URL devueltas cuelgan de aquí. */
    imagenes: `${API}/productos/imagenes`,
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
