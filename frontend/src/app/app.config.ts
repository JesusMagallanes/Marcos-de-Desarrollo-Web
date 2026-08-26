import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withInMemoryScrolling } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { registerLocaleData } from '@angular/common';
import localeEsPe from '@angular/common/locales/es-PE';

import { routes } from './app.routes';
import {
  authInterceptor,
  cacheInterceptor,
  correlacionInterceptor,
  errorInterceptor,
  reintentoInterceptor,
} from './core';

registerLocaleData(localeEsPe, 'es-PE');

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      routes,
      withInMemoryScrolling({ scrollPositionRestoration: 'top', anchorScrolling: 'enabled' }),
    ),

    /**
     * El orden importa. En la ida se ejecutan de arriba abajo; en la vuelta, al revés:
     *
     * `cacheInterceptor` va el PRIMERO de los que tocan la petición porque un
     * acierto de caché corta la cadena ahí mismo: no se reintenta, no se firma y
     * no sale a la red. Ponerlo debajo de `auth` significaría adjuntar un token
     * a una petición que no se va a hacer.
     *
     * `correlacion` se queda por encima para que el identificador se genere
     * también en las que sí salen.
     */
    provideHttpClient(
      withInterceptors([
        correlacionInterceptor,
        cacheInterceptor,
        errorInterceptor,
        reintentoInterceptor,
        authInterceptor,
      ]),
    ),
  ],
};
