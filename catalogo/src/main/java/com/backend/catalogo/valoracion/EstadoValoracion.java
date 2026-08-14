package com.backend.catalogo.valoracion;

/** Fase de moderación de una valoración. */
public enum EstadoValoracion {
    /** Recién enviada por el cliente; aún no la ve la tienda. */
    PENDIENTE,
    /** Aprobada por un administrador; visible en la ficha del producto. */
    APROBADA,
    /** Descartada por un administrador; nunca se publica. */
    RECHAZADA;
}
