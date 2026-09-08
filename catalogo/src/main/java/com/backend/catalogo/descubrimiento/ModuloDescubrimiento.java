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
    DESCUBRE_ALGO_NUEVO("Nuevas oportunidades para explorar", Origen.EXPLORACION);

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
