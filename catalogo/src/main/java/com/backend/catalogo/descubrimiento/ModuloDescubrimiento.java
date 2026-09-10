package com.backend.catalogo.descubrimiento;

/**
 * Los carruseles del Home, con su título y su procedencia.
 *
 * <p>El identificador del módulo es lo que se guarda en {@code impresion} y lo
 * que permite medir CTR por carrusel: sin él no hay forma de saber cuál se gana
 * su sitio y cuál solo ocupa pantalla.
 */
public enum ModuloDescubrimiento {

    SEGUN_TUS_INTERESES("Según tus intereses", Origen.PERSONAL,
            RazonRecomendacion.PERSONAL_INTEREST),
    RELACIONADOS("Productos relacionados", Origen.PERSONAL,
            RazonRecomendacion.CONTENT_SIMILAR),
    LO_MAS_VISTO_EN_TU_ZONA("Lo más visto en %s", Origen.GEO,
            RazonRecomendacion.LOCAL_TREND),
    POPULARES("Lo más popular en SmartZone", Origen.TENDENCIA, RazonRecomendacion.POPULAR),
    DESCUBRE_ALGO_NUEVO("Nuevas oportunidades para explorar", Origen.EXPLORACION,
            RazonRecomendacion.EXPLORATION),

    /*
     * El modulo colaborativo de la fase 2.
     *
     * El titulo es deliberadamente sobrio. «Usuarios como tu compraron» es la
     * formula habitual y es mala por dos motivos: promete una precision que la
     * evidencia no sostiene, y le dice a alguien que el sistema lo ha agrupado
     * con otras personas, que es exactamente la sensacion que hace que una
     * tienda parezca que vigila. Lo que se ensena es el resultado; de donde
     * salio se queda dentro, en `RazonRecomendacion`.
     *
     * Aqui habia tres modulos que no producia nadie, y solo se sabia de uno.
     * SUELEN_IR_JUNTOS se penso para la co-visita en la ficha de producto, pero
     * la co-visita acabo mezclandose dentro de RELACIONADOS, que es donde tiene
     * sentido: quien mira una impresora no quiere otra impresora, quiere el
     * toner. PORQUE_VISTE no se llego a implementar nunca. Y TENDENCIAS quedo
     * desplazado por LO_MAS_VISTO_EN_TU_ZONA, que es lo que sirve el endpoint
     * /tendencias pese al nombre.
     *
     * Los tres se eliminaron. Un modulo que nadie produce ensucia la medicion
     * con una categoria que siempre vale cero, y quien lea el panel tiene que
     * averiguar si es que no funciona o es que no existe. Los encontro la
     * prueba estructural de `ModuloDescubrimientoTest`, que existe para eso.
     */
    OTROS_DESCUBRIERON("Otras personas descubrieron", Origen.COHORTE,
            RazonRecomendacion.CO_VIEWED);

    private final String plantilla;
    private final Origen origen;

    /**
     * La razón que se anota cuando el módulo no dice otra cosa.
     *
     * <p>Casi todos los módulos tienen una sola fuente y entonces coinciden. El
     * colaborativo no: mezcla co-visita y perfiles parecidos en un mismo
     * carrusel, y ahí la razón se anota por ítem. Este valor es el respaldo, y
     * saber que puede quedarse corto es parte de leer bien los datos.
     */
    private final RazonRecomendacion razonPorDefecto;

    ModuloDescubrimiento(String plantilla, Origen origen, RazonRecomendacion razonPorDefecto) {
        this.razonPorDefecto = razonPorDefecto;
        this.plantilla = plantilla;
        this.origen = origen;
    }

    public RazonRecomendacion razonPorDefecto() {
        return razonPorDefecto;
    }

    public Origen origen() {
        return origen;
    }

    /** El título ya redactado; {@code detalle} rellena el hueco si lo hay. */
    public String titulo(String detalle) {
        return plantilla.contains("%s")
                ? plantilla.formatted(detalle == null ? "tu zona" : detalle)
                : plantilla;
    }
}
