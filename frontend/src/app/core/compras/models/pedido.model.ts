/**
 * Estados del pedido. Definidos aquí porque son del dominio de compras.
 *
 * `CONFIRMADO` es el pedido contra entrega: existe y va de camino, pero el
 * dinero llega con el repartidor. No es `PAGADO` —sería mentira— ni `PENDIENTE`,
 * que es un checkout abandonado y no se le enseña al comprador.
 */
export type EstadoPedido =
  'PENDIENTE' | 'CONFIRMADO' | 'PAGADO' | 'EN_TRANSITO' | 'ENTREGADO' | 'CANCELADO';

/** Servicio `compras` (:8083). */
export interface DetallePedido {
  productoId: number;
  /** Copia guardada al comprar: no cambia si luego se edita el producto. */
  productoNombre: string;
  imagen: string | null;
  cantidad: number;
  precioUnitario: number;
  total: number;
}

export interface Pedido {
  id: number;
  /**
   * El número que ve el comprador: `SZ-000042`.
   *
   * Se enseñaba el id de la tabla en crudo —«Pedido #7»—, que no parece un
   * número de pedido y de paso dice cuántas compras lleva la tienda. Lo compone
   * el backend para que el número que ve el comprador y el del panel sean el
   * mismo; `id` sigue siendo la clave con la que se llama a la API.
   */
  numero: string;
  usuarioId: number;
  fecha: string;
  estado: EstadoPedido;
  /* El desglose de lo que se cobró. Las líneas del detalle suman `subtotal`, y
   * `total` es eso más el envío: sin los tres, la tabla del detalle no cuadra. */
  subtotal: number;
  costoEnvio: number;
  total: number;
  /** Nombre del método de pago, no su id. */
  metodoPago: string;
  detalles: DetallePedido[];
}

/**
 * Un pedido contra entrega se paga al recibirlo: hasta entonces el comprador no
 * ha pagado nada, y decírselo evita la llamada preguntando si se le cobró.
 */
export function pagaAlRecibir(pedido: Pedido): boolean {
  return pedido.estado === 'CONFIRMADO';
}

export interface CambioEstadoPedido {
  estado: EstadoPedido;
}

/** Etiquetas para mostrar; los valores crudos son los del backend. */
export const ETIQUETA_ESTADO_PEDIDO: Record<EstadoPedido, string> = {
  PENDIENTE: 'Pendiente',
  // Dice lo que le importa al comprador —que pagará al recibirlo— y no
  // «Confirmado», que no le aclara si le han cobrado ya o no.
  CONFIRMADO: 'Pago al recibir',
  PAGADO: 'Pagado',
  EN_TRANSITO: 'En camino',
  ENTREGADO: 'Entregado',
  CANCELADO: 'Cancelado',
};

/** Clase de Bootstrap por estado, para no repetir el switch en cada vista. */
export const CLASE_ESTADO_PEDIDO: Record<EstadoPedido, string> = {
  PENDIENTE: 'bg-secondary',
  CONFIRMADO: 'bg-warning text-dark',
  PAGADO: 'bg-primary',
  EN_TRANSITO: 'bg-info text-dark',
  ENTREGADO: 'bg-success',
  CANCELADO: 'bg-danger',
};

/** Transiciones permitidas, iguales que las del backend. */
const TRANSICIONES: Record<EstadoPedido, EstadoPedido[]> = {
  PENDIENTE: ['PAGADO', 'CONFIRMADO', 'CANCELADO'],
  // Un contra entrega no pasa por PAGADO: se cobra al entregarlo.
  CONFIRMADO: ['EN_TRANSITO', 'CANCELADO'],
  PAGADO: ['EN_TRANSITO', 'CANCELADO'],
  EN_TRANSITO: ['ENTREGADO', 'CANCELADO'],
  ENTREGADO: [],
  CANCELADO: [],
};

export function puedePasarA(actual: EstadoPedido, siguiente: EstadoPedido): boolean {
  return TRANSICIONES[actual].includes(siguiente);
}

/** Siguiente paso natural del flujo, o null si el pedido ya terminó. */
export function siguienteEstado(actual: EstadoPedido): EstadoPedido | null {
  switch (actual) {
    case 'PENDIENTE':
      return 'PAGADO';
    case 'CONFIRMADO':
    case 'PAGADO':
      return 'EN_TRANSITO';
    case 'EN_TRANSITO':
      return 'ENTREGADO';
    default:
      return null;
  }
}

/** Texto del botón que avanza el pedido al siguiente estado. */
export function accionSiguiente(actual: EstadoPedido): string | null {
  const siguiente = siguienteEstado(actual);
  if (!siguiente) {
    return null;
  }
  return {
    PAGADO: 'Marcar como pagado',
    EN_TRANSITO: 'Marcar en camino',
    ENTREGADO: 'Marcar entregado',
  }[siguiente as 'PAGADO' | 'EN_TRANSITO' | 'ENTREGADO'];
}

export function esEstadoFinal(estado: EstadoPedido): boolean {
  return estado === 'ENTREGADO' || estado === 'CANCELADO';
}

/**
 * Los pedidos que hay que preparar y mandar.
 *
 * El panel de envíos filtraba por `PENDIENTE`, y `PENDIENTE` es un checkout que
 * se abandonó sin pagar. El resultado era el peor de los dos posibles a la vez:
 * los pedidos PAGADOS —los que de verdad hay que enviar— no salían en ninguna
 * pestaña, y en cambio la lista se llenaba de compras que nadie pagó, con un
 * botón que ofrecía marcarlas como pagadas desde el panel de reparto.
 *
 * Son dos estados porque hay dos formas de llegar aquí: prepagado con la
 * pasarela (`PAGADO`) y contra entrega (`CONFIRMADO`, se cobra al entregarlo).
 * Para quien prepara el paquete son lo mismo; la diferencia es que en uno hay
 * que cobrar en la puerta.
 */
export const LISTOS_PARA_ENVIAR: EstadoPedido[] = ['PAGADO', 'CONFIRMADO'];

export function estaListoParaEnviar(estado: EstadoPedido): boolean {
  return LISTOS_PARA_ENVIAR.includes(estado);
}
