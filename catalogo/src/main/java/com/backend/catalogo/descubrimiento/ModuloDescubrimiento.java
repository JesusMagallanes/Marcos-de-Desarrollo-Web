package com.backend.catalogo.descubrimiento;

/**
 * Los carruseles del Home, con su título y su procedencia.
 *
 * <p>El identificador del módulo es lo que se guarda en {@code impresion} y lo
 * que permite medir CTR por carrusel: sin él no hay forma de saber cuál se gana
 * su sitio y cuál solo ocupa pantalla.
 */
public enum ModuloDescubrimiento {

    SEGUN_TUS_INTERESES("Según tus intereses", Origen.PERSONAL),
    PORQUE_VISTE("Porque viste %s", Origen.PERSONAL),
    RELACIONADOS("Productos relacionados", Origen.PERSONAL),
    LO_MAS_VISTO_EN_TU_ZONA("Lo más visto en %s", Origen.GEO),
    TENDENCIAS("Está llamando la atención", Origen.TENDENCIA),
    POPULARES("Lo más popular en SmartZone", Origen.TENDENCIA),
    DESCUBRE_ALGO_NUEVO("Nuevas oportunidades para explorar", Origen.EXPLORACION),

    /*
     * Los dos modulos colaborativos de la fase 2.
     *
     * Los titulos son deliberadamente sobrios. «Usuarios como tu compraron» es
     * la formula habitual y es mala por dos motivos: promete una precision que
     * la evidencia no sostiene, y le dice a alguien que el sistema lo ha
     * agrupado con otras personas, que es exactamente la sensacion que hace que
     * una tienda parezca que vigila. Lo que se ensena es el resultado; de donde
     * salio se queda dentro, en `RazonRecomendacion`.
     */
    SUELEN_IR_JUNTOS("Suele mirarse junto con esto", Origen.COHORTE),
    OTROS_DESCUBRIERON("Otras personas descubrieron", Origen.COHORTE);

    private final String plantilla;
    private final Origen origen;

    ModuloDescubrimiento(String plantilla, Origen origen) {
        this.plantilla = plantilla;
        this.origen = origen;
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
