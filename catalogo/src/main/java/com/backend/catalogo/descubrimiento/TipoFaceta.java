package com.backend.catalogo.descubrimiento;

/**
 * Las dimensiones sobre las que se mide el interés de un sujeto.
 *
 * <p>Discrimina las filas de {@code perfil_faceta}. Son una tabla y no cuatro
 * porque todas tienen la misma forma —sujeto, algo, un score— y separarlas
 * obligaría a repetir la lógica de decaimiento en cada una.
 */
public enum TipoFaceta {
    /** Nodo del árbol de categorías, propagado hacia arriba. */
    CATEGORIA,
    /** Nombre de marca. */
    MARCA,
    /**
     * Característica concreta, en la forma {@code codigo=valor}.
     *
     * <p>Sale de {@code producto_atributo}; aquí no se guarda ninguna
     * característica del catálogo, solo cuánto le interesa al sujeto.
     */
    ATRIBUTO,
    /** Tramo de precio en el que se mueve. */
    PRECIO
}
