/**
 * Solicitudes para vender en la tienda — servicio `usuarios` (:8082).
 *
 * El contrato completo está en `docs/contrato-colaboradores.md`. Aquí solo las
 * formas que viajan por HTTP.
 */

export type TipoPersona = 'NATURAL' | 'JURIDICA';
export type TipoDocumento = 'DNI' | 'CE' | 'RUC';
export type EstadoSolicitud = 'PENDIENTE' | 'APROBADA' | 'RECHAZADA';

/** Qué es cada archivo que sube el solicitante. */
export type TipoAdjunto = 'DOCUMENTO_ANVERSO' | 'DOCUMENTO_REVERSO' | 'FICHA_RUC';

/**
 * De qué depende cada cosa. Se declara aquí, y no repartido por los
 * componentes, porque el backend aplica exactamente estas reglas y tenerlas en
 * un solo sitio evita que el formulario y la API discrepen.
 */
export const REGLAS_POR_PERSONA = {
  NATURAL: {
    documentos: ['DNI', 'CE'] as TipoDocumento[],
    exigeFechaNacimiento: true,
    exigeRepresentante: false,
    adjuntos: ['DOCUMENTO_ANVERSO', 'DOCUMENTO_REVERSO'] as TipoAdjunto[],
  },
  JURIDICA: {
    documentos: ['RUC'] as TipoDocumento[],
    exigeFechaNacimiento: false,
    exigeRepresentante: true,
    adjuntos: ['DOCUMENTO_ANVERSO', 'DOCUMENTO_REVERSO', 'FICHA_RUC'] as TipoAdjunto[],
  },
} as const;

/** Etiquetas para la interfaz. Las mismas que devuelve el backend. */
export const ETIQUETA_ADJUNTO: Record<TipoAdjunto, string> = {
  DOCUMENTO_ANVERSO: 'Anverso del documento',
  DOCUMENTO_REVERSO: 'Reverso del documento',
  FICHA_RUC: 'Ficha RUC',
};

/** Lo que el backend acepta. Se avisa antes de subir 5 MB para nada. */
export const TIPOS_ACEPTADOS = 'image/jpeg,image/png,application/pdf';
export const TAMANO_MAXIMO_BYTES = 5 * 1024 * 1024;

export interface Domicilio {
  direccion: string;
  referencia?: string;
  distrito: string;
  provincia: string;
  departamento: string;
  codigoPostal: string;
  pais?: string;
  /*
   * El punto en el mapa, opcional. Van las dos o no va ninguna.
   *
   * <p>Admiten `null` además de faltar porque la respuesta del backend las trae
   * explícitamente nulas cuando el solicitante no marcó nada, y `DomicilioResponse`
   * extiende esta interfaz.
   */
  latitud?: number | null;
  longitud?: number | null;
}

/** El `usuarioId` NO viaja: sale del token. */
export interface SolicitudRequest {
  tipoPersona: TipoPersona;
  tipoDocumento: TipoDocumento;
  documento: string;
  nombreTitular: string;
  representanteLegal?: string;
  fechaNacimiento?: string;
  nombreComercial: string;
  telefonoContacto: string;
  rubro: string;
  descripcion: string;
  domicilio: Domicilio;
  aceptaTerminos: boolean;
  terminosVersion: string;
}

/** Ficha de un archivo. Nunca los bytes: para verlos hay que pedirlos con token. */
export interface AdjuntoResponse {
  id: number;
  tipo: TipoAdjunto;
  etiqueta: string;
  nombreOriginal: string;
  tipoMime: string;
  tamanoBytes: number;
  subidoEn: string;
  /** `false` si se borró por retención: la ficha sigue, la imagen ya no. */
  disponible: boolean;
}

export interface SubidaResponse {
  id: number;
  tipo: TipoAdjunto;
  etiqueta: string;
  nombreOriginal: string;
  tipoMime: string;
  tamanoBytes: number;
}

export interface DomicilioResponse extends Domicilio {
  pais: string;
  latitud: number | null;
  longitud: number | null;
}

export interface SolicitudResponse {
  id: number;
  estado: EstadoSolicitud;
  tipoPersona: TipoPersona;
  tipoDocumento: TipoDocumento;
  documento: string;
  nombreTitular: string;
  representanteLegal: string | null;
  fechaNacimiento: string | null;
  nombreComercial: string;
  telefonoContacto: string;
  rubro: string;
  descripcion: string;
  domicilio: DomicilioResponse;
  adjuntos: AdjuntoResponse[];
  motivoRechazo: string | null;
  creadaEn: string;
  resueltaEn: string | null;
}

export interface Solicitante {
  id: number;
  nombreCompleto: string;
  email: string;
  rol: string;
}

/** Lo que ve el administrador: la solicitud más quién la envía. */
export interface SolicitudAdminResponse extends SolicitudResponse {
  solicitante: Solicitante;
}

export interface RechazoRequest {
  motivo: string;
}
