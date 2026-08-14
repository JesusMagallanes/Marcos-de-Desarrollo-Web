import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../../usuarios/services/auth.service';

/** Panel admin: ADMINISTRADOR o cualquier permiso de gestión. */
export const adminGuard: CanActivateFn = (_ruta, estado) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.esAdminPanel()) {
    return true;
  }

  if (auth.autenticado()) {
    return router.createUrlTree(['/']);
  }

  return router.createUrlTree(['/login'], {
    queryParams: { redirigir: estado.url },
  });
};
