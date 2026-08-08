/** Estados del envío. Definidos aquí porque son del dominio de compras. */
export type EstadoEnvio = 'PENDIENTE' | 'EN_TRANSITO' | 'ENTREGADO';

/** Servicio `compras` (:8083). El envío se crea al confirmarse el pago. */
export interface Envio {
  id: number;
  pedidoId: number;
  direccion: string;
  estadoEnvio: EstadoEnvio;
  fechaEnvioProgramado: string | null;
  fechaEnvioEntregado: string | null;
}

export interface CambioEstadoEnvio {
  estadoEnvio: EstadoEnvio;
}

export const ETIQUETA_ESTADO_ENVIO: Record<EstadoEnvio, string> = {
  PENDIENTE: 'Por enviar',
  EN_TRANSITO: 'En camino',
  ENTREGADO: 'Entregado',
};

export const CLASE_ESTADO_ENVIO: Record<EstadoEnvio, string> = {
  PENDIENTE: 'bg-secondary',
  EN_TRANSITO: 'bg-info text-dark',
  ENTREGADO: 'bg-success',
};
