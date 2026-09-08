package com.backend.catalogo.descubrimiento;

/**
 * Qué clase de cosa se recomienda.
 *
 * <p>Existe para que el descubrimiento NO quede atado a `producto`. Hoy solo
 * hay productos, pero la plataforma va a recomendar también negocios,
 * servicios y contenido, y una tabla de eventos con una clave foránea a
 * `producto` obligaría a rehacerla entera el día que aparezca el primero.
 */
public enum TipoItem {
    PRODUCTO,
    NEGOCIO,
    SERVICIO,
    PUBLICACION
}
