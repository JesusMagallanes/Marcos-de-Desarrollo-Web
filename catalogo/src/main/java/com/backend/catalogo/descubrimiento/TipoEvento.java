package com.backend.catalogo.descubrimiento;

/**
 * Lo que un sujeto puede hacer, y que dice algo de lo que le interesa.
 *
 * <p>Los PESOS NO están aquí: viven en {@link
 * com.backend.catalogo.descubrimiento.config.PesosDescubrimiento}, que se
 * configura desde properties. Ponerlos en el enum obligaría a recompilar para
 * calibrar el recomendador, que es justo lo que más se toca al principio.
 */
public enum TipoEvento {

    /* ── Señales positivas, de más barata a más cara ── */

    /** Abrió la ficha. Barata y abundante. */
    ITEM_VIEW,
    /** Llegó a las especificaciones o superó el umbral de permanencia. */
    ITEM_VIEW_DEEP,
    /** Entró a una categoría. A menudo es de paso. */
    CATEGORY_VIEW,
    /** Escribió una búsqueda: intención declarada con sus palabras. */
    SEARCH,
    /** Hizo clic en un resultado. Vale más que la búsqueda: acertó. */
    SEARCH_CLICK,
    /**
     * Aplicó un filtro por característica.
     *
     * <p>La señal más específica que se puede capturar sin preguntar: dice el
     * atributo Y el valor exactos. Solo es aprovechable porque los atributos
     * ya son filas en {@code producto_atributo}.
     */
    ATTRIBUTE_FILTER,
    /** Comparó ítems. Está decidiendo, no mirando. */
    COMPARE,
    /** Compartió: interés suficiente para gastar capital social. */
    SHARE,
    /** Guardó en favoritos. Intención explícita y duradera. */
    FAVORITE,
    /** Añadió al carrito. Muy predictivo. */
    ADD_TO_CART,
    /** Valoró: se tomó el trabajo de escribir. */
    REVIEW,
    /** Compró. El máximo — y a partir de ahí ese ítem se suprime. */
    PURCHASE,

    /* ── Señales negativas ── */

    /** Se lo pensó mejor y lo sacó del carrito. */
    REMOVE_FROM_CART,
    /** Cerró o descartó la tarjeta. */
    DISMISS,
    /**
     * «No me interesa».
     *
     * <p>El botón que hace que el sistema sea suyo. Sin una señal negativa
     * explícita, quien recibe malas recomendaciones no tiene forma de
     * corregirlas y deja de mirar.
     */
    NOT_INTERESTED,

    /* ── Exposición ── */

    /**
     * Se le mostró y no lo tocó.
     *
     * <p>No se guarda en {@code evento_interaccion} sino en {@code impresion}:
     * un Home pinta unos sesenta ítems y se hace clic en uno, así que
     * mezclarlas multiplicaría por sesenta la tabla de eventos.
     */
    IMPRESSION;

    /** Si retira el ítem de futuras recomendaciones. */
    public boolean esDescarte() {
        return this == DISMISS || this == NOT_INTERESTED;
    }

    /** Si va a la tabla de impresiones en vez de a la de eventos. */
    public boolean esImpresion() {
        return this == IMPRESSION;
    }
}
