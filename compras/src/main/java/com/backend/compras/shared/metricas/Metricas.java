package com.backend.compras.shared.metricas;

/** Catálogo de nombres de métrica. Idéntico en los cuatro servicios. */
public final class Metricas {

    private Metricas() {
    }

    /* ── Autenticación · OWASP A07 ── */

    /** Contador. Etiquetas: resultado=correcto|fallido, motivo. */
    public static final String AUTENTICACION = "smartzone_seguridad_autenticacion_total";

    /** Medidor. Tokens de acceso emitidos y aún no revocados. */
    public static final String SESIONES_ACTIVAS = "smartzone_seguridad_sesiones_activas";

    /** Contador. Etiquetas: evento=emitido|revocado|invalido, motivo. */
    public static final String TOKEN = "smartzone_seguridad_token_total";

    /* ── Autorización · OWASP A01 ── */

    /** Contador. Etiqueta: recurso (nombre, nunca el id). */
    public static final String AUTORIZACION_DENEGADA = "smartzone_seguridad_autorizacion_denegada_total";

    /** Contador. Etiqueta: rol destino. Operación sensible que conviene vigilar. */
    public static final String CAMBIO_ROL = "smartzone_seguridad_cambio_rol_total";

    /* ── Abuso y disponibilidad · OWASP A04 ── */

    /** Contador. Etiqueta: ambito (autenticacion|pagos|escritura|lectura|chatbot). */
    public static final String RATE_LIMIT = "smartzone_seguridad_rate_limit_total";

    /** Medidor. Claves distintas que el limitador está siguiendo. */
    public static final String RATE_LIMIT_CLAVES = "smartzone_seguridad_rate_limit_claves_activas";

    /* ── Validación de entrada · OWASP A03 ── */

    /** Contador. Etiqueta: tipo (cuerpo|parametro|json|tipo|parametro_faltante). */
    public static final String ENTRADA_RECHAZADA = "smartzone_seguridad_entrada_rechazada_total";

    /* ── Integridad · OWASP A08 ── */

    /** Contador de eventos que delatan manipulación. */
    public static final String INTEGRIDAD = "smartzone_seguridad_integridad_total";

    /* ── Documentos de identidad ── */

    /**
     * Contador. Etiqueta: evento (subido|descargado|rechazado|purgado).
     *
     * <p>`rechazado` es el que merece vigilancia: significa que alguien mandó un
     * archivo que decía ser una imagen y no lo era. Uno suelto es un usuario
     * confundido; una racha, alguien probando.
     */
    public static final String DOCUMENTO = "smartzone_seguridad_documento_total";

    /* ── Moderación de contenido ── */

    /** Contador. Etiqueta: resultado (aprobado|rechazado). */
    public static final String MODERACION = "smartzone_catalogo_moderacion_total";

    /* ── Avisos ── */

    /**
     * Contador. Etiqueta: resultado (enviado|fallido|omitido).
     *
     * <p>Sin esto, un servidor de correo caído es invisible: los avisos fallan en
     * silencio a propósito para no deshacer la operación que los provocó.
     */
    public static final String CORREO = "smartzone_correo_total";

    /* ── Negocio ── */

    /** Contador. Etiqueta: resultado (completada|compensada|fallida). */
    public static final String SAGA = "smartzone_compras_saga_total";

    /** Temporizador de la saga completa, en segundos. */
    public static final String SAGA_DURACION = "smartzone_compras_saga_duracion_segundos";
}
