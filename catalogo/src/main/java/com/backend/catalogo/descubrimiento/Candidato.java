package com.backend.catalogo.descubrimiento;

/**
 * Un producto que podría recomendarse, con lo mínimo para ordenarlo.
 *
 * <p>Proyección y no entidad a propósito: la etapa de recuperación baraja
 * cientos de candidatos y solo necesita el id, el score y con qué diversificar.
 * Cargar entidades completas para descartar el 95 % sería el gasto más caro del
 * Home. Los datos de pintar se piden UNA vez, al final, sobre los pocos que
 * sobreviven.
 */
public interface Candidato {

    Long getItemId();

    Long getCategoriaId();

    Long getMarcaId();

    Double getScore();
}
