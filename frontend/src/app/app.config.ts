import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners, isDevMode } from '@angular/core';
import { provideRouter, withInMemoryScrolling } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideServiceWorker } from '@angular/service-worker';
import { registerLocaleData } from '@angular/common';
import localeEsPe from '@angular/common/locales/es-PE';

import { routes } from './app.routes';
import {
  ConexionService,
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

    /**
     * Arranca el motor offline: oyentes online/offline, purga de caché
     * vencida y primer barrido de la cola (puede heredar operaciones de una
     * sesión anterior). Sin esto, el motor solo existiría si algún componente
     * inyectara el servicio, y las rutas /admin no lo harían.
     */
    provideAppInitializer(() => {
      inject(ConexionService).iniciar();
    }),

    /**
     * Service Worker SOLO para recursos estáticos (app shell, assets): los
     * datos van por la caché propia de IndexedDB, que sabe de TTL e
     * invalidación; dos capas de caché de datos sería doble fuente de verdad.
     * En desarrollo se desactiva porque `ng serve` no genera ngsw.json.
     */
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000',
    }),
  ],
};
