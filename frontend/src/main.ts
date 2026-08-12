import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

/**
 * La pantalla de carga dura lo que tarde la aplicación en estar lista de
 * verdad, no un tiempo fijo.
 *
 * Antes esperaba 2 segundos por reloj: en una conexión rápida sobraban casi
 * dos, y en una lenta se quedaba corta y el usuario veía la tienda vacía
 * mientras seguían llegando los datos.
 *
 * Ahora se espera a `ApplicationRef.whenStable()`. Ese indicador no es solo
 * "Angular arrancó": `HttpClient` da de alta cada petición en curso como tarea
 * pendiente, así que la aplicación no se considera estable hasta que responden
 * las llamadas que dispara la primera pantalla (en la portada, las categorías y
 * los productos). Es exactamente lo que hay que esperar, y se adapta solo a lo
 * que dé la red de cada usuario.
 */

/**
 * Suelo anti-parpadeo. No es "la duración": es lo mínimo para que la animación
 * se lea como intencionada. Sin él, una carga de 80 ms enciende y apaga la
 * pantalla de golpe y parece un fallo de pintado.
 */
const MINIMO_VISIBLE = 400;

/**
 * Techo. Si la API no contesta —está caída, el móvil perdió cobertura—, la
 * aplicación nunca llega a estable y sin este límite el usuario se quedaría
 * mirando la animación para siempre. Pasado el tope se muestra la tienda: cada
 * pantalla ya sabe enseñar su propio error, que es mucho más útil que un
 * spinner eterno.
 */
const MAXIMO_ESPERA = 15000;

const TRANSICION_SALIDA = 700;

function esperar(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** Un frame de margen para que el router cree la primera vista y esta lance
 *  sus peticiones; si se preguntara antes, la app aún parecería estable. */
function siguienteFrame(): Promise<void> {
  return new Promise((resolve) => requestAnimationFrame(() => resolve()));
}

function ocultarCarga(): void {
  const pantalla = document.getElementById('loading-screen');
  if (!pantalla) return;
  pantalla.classList.add('oculta');
  setTimeout(() => pantalla.remove(), TRANSICION_SALIDA);
}

async function arrancar(): Promise<void> {
  const inicio = performance.now();

  try {
    const app = await bootstrapApplication(App, appConfig);
    await siguienteFrame();

    // Lo que ocurra antes: la app queda estable, o se agota el tope.
    await Promise.race([app.whenStable(), esperar(MAXIMO_ESPERA)]);
  } catch (error) {
    // Si el arranque falla, la pantalla se quita igual: dejarla puesta
    // ocultaría el error en lugar de mostrarlo.
    console.error(error);
  } finally {
    const restante = MINIMO_VISIBLE - (performance.now() - inicio);
    if (restante > 0) {
      await esperar(restante);
    }
    ocultarCarga();
  }
}

void arrancar();
