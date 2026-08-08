import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withInMemoryScrolling } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { registerLocaleData } from '@angular/common';
import localeEsPe from '@angular/common/locales/es-PE';

import { routes } from './app.routes';
import {
  authInterceptor,
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

    /** El orden importa. En la ida se ejecutan de arriba abajo; en la vuelta, al revés: */
    provideHttpClient(
      withInterceptors([
        correlacionInterceptor,
        errorInterceptor,
        reintentoInterceptor,
        authInterceptor,
      ]),
    ),
  ],
};
