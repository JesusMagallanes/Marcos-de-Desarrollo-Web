import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

const DURACION_MINIMA_CARGA = 2000;

function ocultarCarga(): void {
  const pantalla = document.getElementById('loading-screen');
  if (!pantalla) return;
  pantalla.classList.add('oculta');
  setTimeout(() => pantalla.remove(), 700);
}

function esperarMinimo(minimo: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, minimo));
}

Promise.all([bootstrapApplication(App, appConfig), esperarMinimo(DURACION_MINIMA_CARGA)])
  .then(() => ocultarCarga())
  .catch((err) => {
    ocultarCarga();
    console.error(err);
  });
