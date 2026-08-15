import { Routes } from '@angular/router';
import {
  adminGuard,
  adminInicioGuard,
  authGuard,
  invitadoGuard,
  permisoGuard,
} from './core';

/**
 * Cada página se carga de forma diferida, así el bundle inicial no arrastra
 * el panel admin ni la gestión de envíos para un visitante anónimo.
 */
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/home/home').then((m) => m.Home),
    title: 'SmartZone — Tienda de tecnología',
  },
  {
    path: 'categoria/:slug',
    loadComponent: () => import('./pages/categoria/categoria').then((m) => m.Categoria),
    title: 'Categoría — SmartZone',
  },
  {
    path: 'producto/:id',
    loadComponent: () =>
      import('./pages/producto-detalle/producto-detalle').then((m) => m.ProductoDetalle),
    title: 'Producto — SmartZone',
  },
  {
    path: 'buscar',
    loadComponent: () => import('./pages/buscar/buscar').then((m) => m.Buscar),
    title: 'Búsqueda — SmartZone',
  },
  {
    path: 'login',
    canActivate: [invitadoGuard],
    loadComponent: () => import('./pages/login/login').then((m) => m.Login),
    title: 'Ingresar — SmartZone',
  },
  {
    path: 'oauth/callback',
    loadComponent: () =>
      import('./pages/oauth-callback/oauth-callback').then((m) => m.OauthCallback),
    title: 'Completando inicio de sesión…',
  },

  /* ── requieren sesión ── */
  {
    path: 'carrito',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/carrito/carrito').then((m) => m.Carrito),
    title: 'Mi carrito — SmartZone',
  },
  {
    path: 'perfil',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/perfil/perfil').then((m) => m.Perfil),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'cuenta' },
      {
        path: 'cuenta',
        loadComponent: () => import('./pages/perfil/cuenta/cuenta').then((m) => m.Cuenta),
        title: 'Mi cuenta — SmartZone',
      },
      {
        path: 'compras',
        loadComponent: () =>
          import('./pages/perfil/mis-compras/mis-compras').then((m) => m.MisCompras),
        title: 'Mis compras — SmartZone',
      },
    ],
  },

  /* ── administración ── */
  /* La ruta antigua /envios redirige a la sección del panel admin. */
  { path: 'envios', redirectTo: 'admin/envios' },

  /*
   * Vender en la tienda. La solicitud solo pide sesión: cualquier cliente puede
   * pedirlo. Publicar productos exige ya el permiso, que llega con el rol
   * COLABORADOR tras la aprobación.
   */
  {
    path: 'vender',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/vender/vender').then((m) => m.Vender),
    title: 'Vender en SmartZone',
  },
  {
    path: 'vender/mis-productos',
    canActivate: [permisoGuard('PRODUCTOS_PROPIOS')],
    loadComponent: () =>
      import('./pages/vender/mis-productos/mis-productos').then((m) => m.MisProductos),
    title: 'Mis productos',
  },

  {
    path: 'admin',
    canActivate: [adminGuard],
    loadComponent: () => import('./pages/admin/admin').then((m) => m.Admin),
    children: [
      {
        path: '',
        pathMatch: 'full',
        canActivate: [adminInicioGuard],
        redirectTo: 'productos',
      },
      {
        path: 'productos',
        canActivate: [permisoGuard('PRODUCTOS_GESTIONAR')],
        loadComponent: () =>
          import('./pages/admin/admin-productos/admin-productos').then((m) => m.AdminProductos),
        title: 'Admin · Productos',
      },
      {
        path: 'descuentos',
        canActivate: [permisoGuard('DESCUENTOS_GESTIONAR')],
        loadComponent: () =>
          import('./pages/admin/admin-descuentos/admin-descuentos').then((m) => m.AdminDescuentos),
        title: 'Admin · Descuentos',
      },
      {
        path: 'categorias',
        canActivate: [permisoGuard('CATEGORIAS_GESTIONAR')],
        loadComponent: () =>
          import('./pages/admin/admin-categorias/admin-categorias').then((m) => m.AdminCategorias),
        title: 'Admin · Categorías',
      },
      {
        path: 'marcas',
        canActivate: [permisoGuard('MARCAS_GESTIONAR')],
        loadComponent: () =>
          import('./pages/admin/admin-marcas/admin-marcas').then((m) => m.AdminMarcas),
        title: 'Admin · Marcas',
      },
      {
        path: 'usuarios',
        canActivate: [permisoGuard('USUARIOS_GESTIONAR')],
        loadComponent: () =>
          import('./pages/admin/admin-usuarios/admin-usuarios').then((m) => m.AdminUsuarios),
        title: 'Admin · Usuarios',
      },
      {
        path: 'roles',
        canActivate: [permisoGuard('ROLES_GESTIONAR')],
        loadComponent: () => import('./pages/admin/admin-roles/admin-roles').then((m) => m.AdminRoles),
        title: 'Admin · Roles y permisos',
      },
      {
        path: 'envios',
        canActivate: [permisoGuard('PEDIDOS_GESTIONAR')],
        loadComponent: () =>
          import('./pages/admin/admin-envios/admin-envios').then((m) => m.AdminEnvios),
        title: 'Admin · Envíos',
      },
      {
        path: 'metodos-pago',
        canActivate: [permisoGuard('METODOS_PAGO_GESTIONAR')],
        loadComponent: () =>
          import('./pages/admin/admin-metodos-pago/admin-metodos-pago').then(
            (m) => m.AdminMetodosPago,
          ),
        title: 'Admin · Métodos de pago',
      },
      {
        path: 'valoraciones',
        canActivate: [permisoGuard('VALORACIONES_GESTIONAR')],
        loadComponent: () =>
          import('./pages/admin/admin-valoraciones/admin-valoraciones').then(
            (m) => m.AdminValoraciones,
          ),
        title: 'Admin · Valoraciones',
      },
      {
        path: 'guias',
        canActivate: [permisoGuard('GUIAS_GESTIONAR')],
        loadComponent: () =>
          import('./pages/admin/admin-guias/admin-guias').then((m) => m.AdminGuias),
        title: 'Admin · Guías',
      },
    ],
  },

  /* ── informativas ── */
  {
    path: 'guias',
    loadComponent: () => import('./pages/guias/guias').then((m) => m.Guias),
    title: 'Aprende con nosotros',
  },
  {
    path: 'guias/:slug',
    loadComponent: () => import('./pages/guias/guia-detalle').then((m) => m.GuiaDetalle),
    title: 'Guía',
  },
  {
    path: 'somos',
    loadComponent: () => import('./pages/estaticas/somos').then((m) => m.Somos),
    title: 'Quiénes somos — SmartZone',
  },
  {
    path: 'canales',
    loadComponent: () => import('./pages/estaticas/canales').then((m) => m.Canales),
    title: 'Canales de atención — SmartZone',
  },
  {
    path: 'metodos-pago',
    loadComponent: () => import('./pages/estaticas/metodos-pago').then((m) => m.MetodosPago),
    title: 'Métodos de pago — SmartZone',
  },

  {
    path: '**',
    loadComponent: () => import('./pages/no-encontrado/no-encontrado').then((m) => m.NoEncontrado),
    title: 'Página no encontrada',
  },
];

