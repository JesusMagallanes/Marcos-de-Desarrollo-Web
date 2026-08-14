import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../../usuarios/services/auth.service';

/** Staff de tienda (pedidos y envíos): rol clásico o permiso de gestión. */
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
