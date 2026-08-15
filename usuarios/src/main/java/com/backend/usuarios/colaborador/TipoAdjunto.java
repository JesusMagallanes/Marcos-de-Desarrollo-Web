package com.backend.usuarios.colaborador;

/** Qué es cada fichero que sube el solicitante. */
public enum TipoAdjunto {

    /** La cara del documento donde salen la foto y el número. */
    DOCUMENTO_ANVERSO("Anverso del documento"),

    /**
     * El reverso. Se pide porque es donde va la fecha de emisión y el código de
     * verificación: con solo el anverso, un recorte de una foto ajena cuela.
     */
    DOCUMENTO_REVERSO("Reverso del documento"),

    /** Ficha RUC de SUNAT. Solo para empresas. */
    FICHA_RUC("Ficha RUC");

    private final String etiqueta;

    TipoAdjunto(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Texto para enseñar en el formulario y en la bandeja. */
    public String etiqueta() {
        return etiqueta;
    }
}
