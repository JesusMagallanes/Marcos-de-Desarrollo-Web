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
   * Quién recibe y dónde, en partes. Solo venía `direccion`, la línea de la
   * etiqueta: con eso el comprador no puede comprobar el distrito, el código
   * postal ni a nombre de quién va, que es lo que querría mirar justamente
   * cuando manda el pedido a otra persona.
   */
  receptorNombre: string | null;
  calle: string | null;
  numero: string | null;
  codigoPostal: string | null;
  distrito: string | null;
  provincia: string | null;
  departamento: string | null;
  pais: string | null;

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

/**
 * La dirección completa en una línea, a partir de las partes.
 *
 * Se compone aquí y no se usa el campo `direccion` del backend porque aquel es
 * la etiqueta corta —calle, distrito, provincia— y al comprador que está
 * comprobando a dónde va su pedido le falta justo lo que no lleva: el código
 * postal y el departamento.
 */
export function destinoCompleto(envio: Envio): string {
  const calle = [envio.calle, envio.numero].filter(Boolean).join(' ');
  const zona = [envio.distrito, envio.provincia, envio.departamento].filter(Boolean);
  // Lima, Lima, Lima no le dice nada a nadie.
  const zonaUnica = zona.filter((parte, i) => i === 0 || parte !== zona[i - 1]);
  return [calle || null, ...zonaUnica, envio.codigoPostal].filter(Boolean).join(', ');
}
