import { API } from '../config/api.base';

/** Cuánto vale una respuesta guardada, y para qué recurso. */
export interface PoliticaCache {
  /** Se compara contra la ruta SIN query. */
  readonly patron: RegExp;
  readonly ttlMs: number;
  /** Para leerlo en las pruebas y en los logs. */
  readonly nombre: string;
}

const SEGUNDO = 1000;
const MINUTO = 60 * SEGUNDO;
const HORA = 60 * MINUTO;

/** La ruta de un recurso, ya sin el prefijo del entorno. */
const ruta = (patron: string) =>
  new RegExp(`^${API.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}${patron}$`);

/**
 * Qué se guarda en caché y cuánto.
 *
 * <h4>Es una lista blanca, y eso es lo importante</h4>
 *
 * <p>Lo que no está aquí NO se cachea. Al revés —cachear por defecto y llevar
 * una lista de excepciones— el día que alguien añade un endpoint nuevo, este se
 * cachea sin que nadie lo haya decidido; y si ese endpoint devuelve el carrito,
 * un pedido o unos datos personales, el fallo es serio y silencioso.
 *
 * <h4>Solo entra lo que es igual para todo el mundo</h4>
 *
 * <p>La caché se guarda por URL, sin mirar quién pregunta, así que solo puede
 * contener respuestas que no dependan del usuario. Por eso están el catálogo y
 * el ubigeo, y no están el carrito, los pedidos, los envíos, los pagos, el
 * perfil, ni `/productos/mios` y `/productos/moderacion`, que enseñan cosas
 * distintas según quién mire.
 *
 * <p>Como red de seguridad adicional, la caché se vacía entera al entrar y al
 * salir de la cuenta (ver `AuthService`).
 *
 * <h4>Los plazos</h4>
 *
 * <p>Cada uno sale de cuánto tarda en cambiar el dato de verdad, no de cuánto
 * nos gustaría ahorrar. El ubigeo lo cambia una migración; las categorías y
 * marcas, un administrador de vez en cuando; los productos se mueven con cada
 * descuento y cada compra, y por eso duran un minuto. Además, cualquier
 * escritura sobre un recurso tira su caché al instante, así que estos plazos
 * solo aplican a los cambios que vienen de FUERA de esta pestaña.
 */
export const POLITICAS: readonly PoliticaCache[] = [
  // Lo cambia una migración. No cambia mientras la pestaña esté abierta.
  { nombre: 'ubigeo', patron: ruta('/ubigeo/.*'), ttlMs: 24 * HORA },

  // Las toca un administrador muy de vez en cuando.
  { nombre: 'categorias', patron: ruta('/categorias(/slug/[^/]+)?'), ttlMs: 10 * MINUTO },
  { nombre: 'marcas', patron: ruta('/marcas(/categoria/\\d+)?'), ttlMs: 10 * MINUTO },
  { nombre: 'metodos-pago', patron: ruta('/metodos-pago'), ttlMs: 10 * MINUTO },

  /*
   * Contenido editorial: cambia cuando se publica una guía.
   *
   * El `(?!admin)` deja fuera `/guias/admin/**`, que es el panel y sí trae los
   * borradores. Sin él, `/guias/admin` habría entrado por la puerta del detalle
   * público, igual que habría pasado con `/productos/mios`.
   */
  { nombre: 'guias', patron: ruta('/guias(/(?!admin)[^/]+)?'), ttlMs: 5 * MINUTO },
  { nombre: 'valoraciones-top', patron: ruta('/valoraciones/top'), ttlMs: 5 * MINUTO },

  /*
   * Productos: la lista, el detalle y el listado por categoría.
   *
   * Un minuto y no más porque aquí viven el precio, el stock y los descuentos.
   * El plazo no es un riesgo de cobro —el importe se recalcula en el servidor al
   * pagar, leyendo catálogo— sino de que la ficha enseñe un stock que ya no
   * está; y para eso un minuto es de sobra.
   *
   * `/productos/mios` y `/productos/moderacion` quedan FUERA a propósito: son
   * las dos rutas de esta familia que enseñan cosas distintas según quién
   * pregunte. De ahí el `\\d+` y el `categoria/` en vez de un comodín.
   */
  { nombre: 'productos', patron: ruta('/productos'), ttlMs: MINUTO },
  { nombre: 'portada', patron: ruta('/productos/portada'), ttlMs: MINUTO },
  { nombre: 'producto', patron: ruta('/productos/\\d+'), ttlMs: MINUTO },
  { nombre: 'productos-categoria', patron: ruta('/productos/categoria/[^/]+'), ttlMs: MINUTO },
];

/**
 * La política que aplica a una URL, o `undefined` si no se cachea.
 *
 * @param url la URL completa de la petición, con query si la tiene
 */
export function politicaPara(url: string): PoliticaCache | undefined {
  const sinQuery = url.split('?')[0];
  return POLITICAS.find((p) => p.patron.test(sinQuery));
}

/**
 * El recurso al que pertenece una URL: `/api/productos/42` → `/api/productos`.
 *
 * <p>Es lo que permite que una escritura tire la caché de su familia entera sin
 * que nadie tenga que acordarse de invalidar a mano: un descuento se aplica en
 * `POST /api/productos/descuento` y con esto invalida también la lista y cada
 * ficha, que es justo donde se vería el precio viejo.
 */
export function recursoDe(url: string): string {
  const sinQuery = url.split('?')[0];
  if (!sinQuery.startsWith(API)) {
    return sinQuery;
  }
  const resto = sinQuery.slice(API.length).split('/').filter(Boolean);
  return resto.length ? `${API}/${resto[0]}` : API;
}
