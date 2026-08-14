import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../../usuarios/services/auth.service';
import { SECCIONES_ADMIN } from '../config/admin-secciones';

/**
 * /admin sin sección: lleva a la primera sección que el rol de la sesión pueda
 * gestionar, en vez de redirigir siempre a «productos».
 */
export const adminInicioGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const primera = SECCIONES_ADMIN.find((s) => auth.tienePermiso(s.permiso));
  return router.createUrlTree(['/admin', primera?.ruta ?? 'productos']);
};
