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
  referencia: string | null;
  telefonoContacto: string | null;

  /*
   * Épica 3: a qué distancia está el destino y cuánto se tarda. `null` si el
   * comprador no compartió su ubicación.
   *
   * `distanciaEsEstimada` viene siempre en true cuando hay cálculo, y hay que
   * enseñarlo: NO es una ruta real, es línea recta con un ajuste. Un "37 min"
   * a secas se leería como un dato exacto.
   */
  distanciaKm: number | null;
  minutosEstimados: number | null;
  distanciaEsEstimada: boolean | null;
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
