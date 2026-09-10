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
    PORQUE_VISTE("Porque viste %s", Origen.PERSONAL, RazonRecomendacion.PERSONAL_INTEREST),
    RELACIONADOS("Productos relacionados", Origen.PERSONAL,
            RazonRecomendacion.CONTENT_SIMILAR),
    LO_MAS_VISTO_EN_TU_ZONA("Lo más visto en %s", Origen.GEO,
            RazonRecomendacion.LOCAL_TREND),
    TENDENCIAS("Está llamando la atención", Origen.TENDENCIA,
            RazonRecomendacion.LOCAL_TREND),
    POPULARES("Lo más popular en SmartZone", Origen.TENDENCIA, RazonRecomendacion.POPULAR),
    DESCUBRE_ALGO_NUEVO("Nuevas oportunidades para explorar", Origen.EXPLORACION,
            RazonRecomendacion.EXPLORATION),

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
    SUELEN_IR_JUNTOS("Suele mirarse junto con esto", Origen.COHORTE,
            RazonRecomendacion.CO_VIEWED),
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
