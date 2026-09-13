import { inject } from '@angular/core';
import { RedirectFunction } from '@angular/router';
import { AuthService } from '../../usuarios/services/auth.service';
import { SECCIONES_ADMIN } from '../config/admin-secciones';

/**
 * /admin sin sección: lleva a la primera sección que el rol de la sesión pueda
 * gestionar, en vez de redirigir siempre a «productos».
 *
 * <p>Es una función de `redirectTo`, no una guardia. Fue guardia, y estaba en
 * una ruta que además tenía `redirectTo: 'productos'`: Angular resuelve la
 * redirección ANTES que las guardias, así que esta nunca llegaba a ejecutarse
 * y todo el mundo caía en «productos» tuviera o no ese permiso. Desde Angular
 * 20 esa combinación es directamente un error de configuración (NG04014) que
 * impide arrancar la aplicación en desarrollo; en el build de producción no se
 * valida, y por eso el fallo pasó desapercibido.
 */
export const adminInicioRedirect: RedirectFunction = () => {
  const auth = inject(AuthService);
  const primera = SECCIONES_ADMIN.find((s) => auth.tienePermiso(s.permiso));
  return `/admin/${primera?.ruta ?? 'productos'}`;
};
