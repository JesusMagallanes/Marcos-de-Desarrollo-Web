/**
 * Setup global de pruebas unitarias.
 *
 * <p>jsdom no implementa IndexedDB y varios servicios la usan al arrancar;
 * el polyfill se registra aquí para que TODOS los archivos de prueba lo
 * tengan disponible desde antes de cargar cualquier módulo de la app,
 * sin depender del orden de imports de cada spec.
 */
import 'fake-indexeddb/auto';
