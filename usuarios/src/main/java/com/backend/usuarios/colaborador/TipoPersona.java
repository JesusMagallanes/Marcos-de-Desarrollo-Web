package com.backend.usuarios.colaborador;

import java.util.Set;

/**
 * Quién solicita vender.
 *
 * <p>No es una etiqueta: de aquí cuelga qué documento vale, qué campos son
 * obligatorios y qué adjuntos hay que mandar. Por eso cada valor lleva sus
 * propias reglas en vez de repartirlas por ifs en el servicio.
 */
public enum TipoPersona {

    /** Alguien que vende sus cosas a su nombre. */
    NATURAL(Set.of(TipoDocumento.DNI, TipoDocumento.CE)),

    /** Una empresa. Necesita RUC y alguien que firme por ella. */
    JURIDICA(Set.of(TipoDocumento.RUC));

    private final Set<TipoDocumento> documentosValidos;

    TipoPersona(Set<TipoDocumento> documentosValidos) {
        this.documentosValidos = documentosValidos;
    }

    public boolean admite(TipoDocumento tipo) {
        return tipo != null && documentosValidos.contains(tipo);
    }

    public Set<TipoDocumento> documentosValidos() {
        return documentosValidos;
    }

    /** Una empresa no tiene fecha de nacimiento; una persona sí, y se le exige. */
    public boolean exigeFechaNacimiento() {
        return this == NATURAL;
    }

    /** Solo la empresa necesita declarar quién firma por ella. */
    public boolean exigeRepresentante() {
        return this == JURIDICA;
    }

    /**
     * Qué adjuntos hay que mandar.
     *
     * <p>La empresa manda además la ficha del RUC, y el anverso y reverso del
     * documento de su representante: la ficha dice que la empresa existe, el
     * documento dice quién es quien la está inscribiendo aquí.
     */
    public Set<TipoAdjunto> adjuntosExigidos() {
        return this == JURIDICA
                ? Set.of(TipoAdjunto.DOCUMENTO_ANVERSO, TipoAdjunto.DOCUMENTO_REVERSO,
                        TipoAdjunto.FICHA_RUC)
                : Set.of(TipoAdjunto.DOCUMENTO_ANVERSO, TipoAdjunto.DOCUMENTO_REVERSO);
    }
}
