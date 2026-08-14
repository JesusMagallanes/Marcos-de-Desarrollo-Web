import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../../usuarios/services/auth.service';

/**
 * Guarda una ruta por permiso, p. ej. una sección del panel admin.
 *
 * @param permiso Código del permiso, p. ej. `PERMISO_PRODUCTOS_GESTIONAR`.
 */
export function permisoGuard(permiso: string): CanActivateFn {
  return (_ruta, estado) => {
    const auth = inject(AuthService);
    const router = inject(Router);

    if (auth.tienePermiso(permiso)) {
      return true;
    }

    if (auth.autenticado()) {
      return router.createUrlTree(['/']);
    }

    return router.createUrlTree(['/login'], {
      queryParams: { redirigir: estado.url },
    });
  };
}
