import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../../usuarios/services/auth.service';

/** EMPLEADO o ADMINISTRADOR: cubre la gestión de envíos y pedidos. */
export const staffGuard: CanActivateFn = (_ruta, estado) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.esStaff()) {
    return true;
  }

  if (auth.autenticado()) {
    return router.createUrlTree(['/']);
  }

  return router.createUrlTree(['/login'], {
    queryParams: { redirigir: estado.url },
  });
};
