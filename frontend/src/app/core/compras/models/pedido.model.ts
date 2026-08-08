/** Estados del pedido. Definidos aquí porque son del dominio de compras. */
export type EstadoPedido = 'PENDIENTE' | 'PAGADO' | 'EN_TRANSITO' | 'ENTREGADO' | 'CANCELADO';

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
  usuarioId: number;
  fecha: string;
  estado: EstadoPedido;
  total: number;
  /** Nombre del método de pago, no su id. */
  metodoPago: string;
  detalles: DetallePedido[];
}

export interface CambioEstadoPedido {
  estado: EstadoPedido;
}

/** Etiquetas para mostrar; los valores crudos son los del backend. */
export const ETIQUETA_ESTADO_PEDIDO: Record<EstadoPedido, string> = {
  PENDIENTE: 'Pendiente',
  PAGADO: 'Pagado',
  EN_TRANSITO: 'En camino',
  ENTREGADO: 'Entregado',
  CANCELADO: 'Cancelado',
};

/** Clase de Bootstrap por estado, para no repetir el switch en cada vista. */
export const CLASE_ESTADO_PEDIDO: Record<EstadoPedido, string> = {
  PENDIENTE: 'bg-secondary',
  PAGADO: 'bg-primary',
  EN_TRANSITO: 'bg-info text-dark',
  ENTREGADO: 'bg-success',
  CANCELADO: 'bg-danger',
};

/** Transiciones permitidas, iguales que las del backend. */
const TRANSICIONES: Record<EstadoPedido, EstadoPedido[]> = {
  PENDIENTE: ['PAGADO', 'CANCELADO'],
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
