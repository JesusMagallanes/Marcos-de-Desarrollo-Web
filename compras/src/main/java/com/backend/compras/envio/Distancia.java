package com.backend.compras.envio;

import java.math.BigDecimal;

/**
 * Distancia y tiempo estimado desde la tienda hasta un punto de entrega.
 *
 * <p><b>Lo que esto es y lo que no.</b> Calcula la distancia en línea recta
 * (fórmula del semiverseno) y estima el tiempo con una velocidad media. <b>No es
 * una ruta</b>: no conoce las calles, ni los sentidos únicos, ni el tráfico.
 *
 * <p>Se hace así a propósito y no llamando a un servicio de rutas porque un
 * cálculo local no depende de nadie, no cuesta dinero, no añade una clave de API
 * que rotar y no deja de funcionar cuando el proveedor se cae. Para lo que se
 * necesita —agrupar repartos por zona y saber qué está lejos— basta.
 *
 * <p>Lo importante es que la interfaz lo diga: enseñar «23 min» sin más sería
 * mentir. Por eso {@link #esEstimacion()} existe y la respuesta lo marca.
 */
public final class Distancia {

    private Distancia() {
    }

    private static final double RADIO_TIERRA_KM = 6371.0;

    /**
     * Cuánto más se recorre por calle que en línea recta.
     *
     * <p>En una ciudad con trama de manzanas el recorrido real ronda un 30 % más
     * que la recta. Es un ajuste grosero, pero sin él la estimación siempre se
     * queda corta, y quedarse corto en un reparto es peor que pasarse.
     */
    private static final double FACTOR_CALLEJERO = 1.3;

    /**
     * Velocidad media de reparto urbano, contando semáforos y paradas.
     *
     * <p>18 km/h parece poco visto en un velocímetro, y es justo el número que
     * sale de dividir kilómetros entre el tiempo real de una jornada de reparto
     * en ciudad.
     */
    private static final double VELOCIDAD_KM_H = 18.0;

    /** Resultado del cálculo. */
    public record Estimacion(double kilometros, int minutos) {

        /**
         * Siempre true: sirve para que quien consuma esto no lo confunda con un
         * tiempo de ruta real.
         */
        public boolean esEstimacion() {
            return true;
        }
    }

    /**
     * @return la estimación, o {@code null} si falta alguna coordenada
     */
    public static Estimacion desdeLaTienda(BigDecimal tiendaLat, BigDecimal tiendaLon,
            BigDecimal destinoLat, BigDecimal destinoLon) {

        if (tiendaLat == null || tiendaLon == null || destinoLat == null || destinoLon == null) {
            return null;
        }

        double km = enLineaRecta(
                tiendaLat.doubleValue(), tiendaLon.doubleValue(),
                destinoLat.doubleValue(), destinoLon.doubleValue()) * FACTOR_CALLEJERO;

        int minutos = (int) Math.ceil(km / VELOCIDAD_KM_H * 60);

        // Redondeo a un decimal: dar "3,7 km" sugiere una precisión que el
        // cálculo tiene; dar "3,74812 km" sugiere una que no tiene.
        return new Estimacion(Math.round(km * 10) / 10.0, Math.max(minutos, 1));
    }

    /**
     * Semiverseno. Trata la Tierra como una esfera, con un error de hasta el
     * 0,5 % frente al elipsoide real: irrelevante al lado de no conocer las
     * calles.
     */
    private static double enLineaRecta(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        return RADIO_TIERRA_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
