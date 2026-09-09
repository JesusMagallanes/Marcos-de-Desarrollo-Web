package com.backend.catalogo.descubrimiento;

/**
 * Un candidato que no ha olvidado de dónde viene.
 *
 * <p>La proyección {@link Candidato} que devuelven los generadores trae lo
 * mínimo para ordenar y nada más, que es lo correcto para leer de la base. Pero
 * en cuanto candidatos de distintos generadores compiten por el mismo hueco, el
 * origen deja de ser un detalle: es lo que decide con qué peso entra cada uno y
 * lo que permite, después, saber por qué ganó.
 *
 * <p>Implementa {@code Candidato} a propósito, para poder atravesar la etapa de
 * diversificación sin convertirlo a otra cosa por el camino.
 */
public record CandidatoConRazon(
        Long itemId,
        Long categoriaId,
        Long marcaId,
        double score,
        Origen origen,
        RazonRecomendacion razon) implements Candidato {

    /** Envuelve lo que devolvió un generador, poniéndole nombre a su origen. */
    public static CandidatoConRazon de(Candidato base, Origen origen, RazonRecomendacion razon) {
        return new CandidatoConRazon(base.getItemId(), base.getCategoriaId(),
                base.getMarcaId(), base.getScore() == null ? 0.0 : base.getScore(),
                origen, razon);
    }

    /** El mismo candidato con otro score, conservando su procedencia. */
    public CandidatoConRazon conScore(double nuevo) {
        return new CandidatoConRazon(itemId, categoriaId, marcaId, nuevo, origen, razon);
    }

    @Override
    public Long getItemId() {
        return itemId;
    }

    @Override
    public Long getCategoriaId() {
        return categoriaId;
    }

    @Override
    public Long getMarcaId() {
        return marcaId;
    }

    @Override
    public Double getScore() {
        return score;
    }
}
