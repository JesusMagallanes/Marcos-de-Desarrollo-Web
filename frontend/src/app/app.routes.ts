import { Routes } from '@angular/router';
import { adminGuard, authGuard, invitadoGuard, staffGuard } from './core';

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

  /* ── staff ── */
  {
    path: 'envios',
    canActivate: [staffGuard],
    loadComponent: () => import('./pages/envios/envios').then((m) => m.Envios),
    title: 'Envíos — SmartZone',
  },

  /* ── administración ── */
  {
    path: 'admin',
    canActivate: [adminGuard],
    loadComponent: () => import('./pages/admin/admin').then((m) => m.Admin),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'productos' },
      {
        path: 'productos',
        loadComponent: () =>
          import('./pages/admin/admin-productos/admin-productos').then((m) => m.AdminProductos),
        title: 'Admin · Productos',
      },
      {
        path: 'descuentos',
        loadComponent: () =>
          import('./pages/admin/admin-descuentos/admin-descuentos').then((m) => m.AdminDescuentos),
        title: 'Admin · Descuentos',
      },
      {
        path: 'categorias',
        loadComponent: () =>
          import('./pages/admin/admin-categorias/admin-categorias').then((m) => m.AdminCategorias),
        title: 'Admin · Categorías',
      },
      {
        path: 'marcas',
        loadComponent: () =>
          import('./pages/admin/admin-marcas/admin-marcas').then((m) => m.AdminMarcas),
        title: 'Admin · Marcas',
      },
      {
        path: 'usuarios',
        loadComponent: () =>
          import('./pages/admin/admin-usuarios/admin-usuarios').then((m) => m.AdminUsuarios),
        title: 'Admin · Usuarios',
      },
      {
        path: 'metodos-pago',
        loadComponent: () =>
          import('./pages/admin/admin-metodos-pago/admin-metodos-pago').then(
            (m) => m.AdminMetodosPago,
          ),
        title: 'Admin · Métodos de pago',
      },
      {
        path: 'valoraciones',
        loadComponent: () =>
          import('./pages/admin/admin-valoraciones/admin-valoraciones').then(
            (m) => m.AdminValoraciones,
          ),
        title: 'Admin · Valoraciones',
      },
      {
        path: 'guias',
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
