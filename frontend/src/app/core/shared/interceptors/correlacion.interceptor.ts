import { HttpInterceptorFn } from '@angular/common/http';
import { CABECERA_CORRELACION } from '../config/constantes';

/** Añade `X-Correlation-Id` a cada petición. */
const correlacionSesion = crearIdentificador();

export const correlacionInterceptor: HttpInterceptorFn = (req, next) => {
  return next(
    req.clone({
      setHeaders: { [CABECERA_CORRELACION]: correlacionSesion },
    }),
  );
};

function crearIdentificador(): string {
  // `randomUUID` exige contexto seguro (https o localhost); el respaldo cubre
  // el resto de casos sin romper la aplicación.
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return `web-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}
