/** Secciones del panel admin y el permiso que abre cada una. */
export const SECCIONES_ADMIN: readonly {
  ruta: string;
  icono: string;
  etiqueta: string;
  permiso: string;
}[] = [
  { ruta: 'productos', icono: 'fa-box', etiqueta: 'Productos', permiso: 'PRODUCTOS_GESTIONAR' },
  { ruta: 'descuentos', icono: 'fa-percent', etiqueta: 'Descuentos', permiso: 'DESCUENTOS_GESTIONAR' },
  { ruta: 'categorias', icono: 'fa-layer-group', etiqueta: 'Categorías', permiso: 'CATEGORIAS_GESTIONAR' },
  { ruta: 'marcas', icono: 'fa-tag', etiqueta: 'Marcas', permiso: 'MARCAS_GESTIONAR' },
  { ruta: 'usuarios', icono: 'fa-users', etiqueta: 'Usuarios', permiso: 'USUARIOS_GESTIONAR' },
  // Van junto a Usuarios y a Productos porque es donde el administrador las
  // busca: una concede un rol, la otra decide qué se ve en la tienda.
  { ruta: 'solicitudes', icono: 'fa-id-card', etiqueta: 'Solicitudes de venta', permiso: 'USUARIOS_GESTIONAR' },
  { ruta: 'moderacion-productos', icono: 'fa-clipboard-check', etiqueta: 'Revisar productos', permiso: 'PRODUCTOS_GESTIONAR' },
  { ruta: 'roles', icono: 'fa-user-shield', etiqueta: 'Roles y permisos', permiso: 'ROLES_GESTIONAR' },
  { ruta: 'envios', icono: 'fa-truck', etiqueta: 'Envíos', permiso: 'PEDIDOS_GESTIONAR' },
  { ruta: 'valoraciones', icono: 'fa-comment', etiqueta: 'Valoraciones', permiso: 'VALORACIONES_GESTIONAR' },
  { ruta: 'metodos-pago', icono: 'fa-credit-card', etiqueta: 'Métodos de pago', permiso: 'METODOS_PAGO_GESTIONAR' },
  { ruta: 'guias', icono: 'fa-book-open', etiqueta: 'Guías de ayuda', permiso: 'GUIAS_GESTIONAR' },
];
