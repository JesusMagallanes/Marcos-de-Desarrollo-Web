package com.backend.usuarios.colaborador;

/**
 * Estados por los que pasa una solicitud para vender en la tienda.
 *
 * <p>Las transiciones válidas viven aquí y no repartidas por el servicio: es la
 * única forma de que nadie invente un camino nuevo sin darse cuenta.
 *
 * <pre>
 *   PENDIENTE ──► APROBADA    (el administrador acepta; el usuario pasa a COLABORADOR)
 *   PENDIENTE ──► RECHAZADA   (con motivo; puede volver a solicitar)
 * </pre>
 *
 * De APROBADA no se sale: retirar el rol es otra operación
 * ({@code PATCH /api/usuarios/{id}/rol}), no una transición de la solicitud.
 */
public enum EstadoSolicitud {

    PENDIENTE,
    APROBADA,
    RECHAZADA;

    /** Sigue esperando decisión del administrador. */
    public boolean estaAbierta() {
        return this == PENDIENTE;
    }

    /**
     * Solo se puede resolver lo que está pendiente. Aprobar dos veces, o
     * rechazar algo ya aprobado, es un conflicto y no una operación sin efecto:
     * si el administrador lo intenta, quiere saber que llegó tarde.
     */
    public boolean admiteResolucion() {
        return this == PENDIENTE;
    }
}
