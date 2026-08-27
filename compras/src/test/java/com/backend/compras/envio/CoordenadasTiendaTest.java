package com.backend.compras.envio;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

/**
 * El punto de salida de los repartos, y su signo.
 *
 * <p>Esta clase existe por un fallo concreto: alguien leyó
 * {@code ${TIENDA_LATITUD:-12.046374}} como la sintaxis de bash
 * {@code ${VAR:-defecto}} y «corrigió» el guion. No lo era. En Spring el
 * separador es el {@code :} y todo lo que va detrás es el valor por defecto,
 * así que el {@code -} formaba parte del número.
 *
 * <p>Lima está en el hemisferio SUR y al OESTE de Greenwich: las dos
 * coordenadas son negativas. Con el signo cambiado la tienda se mudaba al sur
 * de la India, y como el destino que manda el comprador sí es negativo, cada
 * envío se calculaba contra un punto casi antipodal: unos 19 000 km y varios
 * días de reparto en una pantalla que organiza motos por Lima.
 *
 * <p>No lo cazó ninguna prueba porque el número está en la configuración y no
 * en el código, y porque {@link Distancia} calcula igual de bien una distancia
 * absurda que una razonable. Se comprueban los dos sitios donde vive el valor
 * por defecto —el properties y la anotación— porque uno tapaba al otro.
 */
@DisplayName("Coordenadas de la tienda")
class CoordenadasTiendaTest {

    /** El centro de Lima, con un margen generoso: esto vigila el signo. */
    private static final BigDecimal LAT_MIN = new BigDecimal("-13");
    private static final BigDecimal LAT_MAX = new BigDecimal("-11");
    private static final BigDecimal LON_MIN = new BigDecimal("-78");
    private static final BigDecimal LON_MAX = new BigDecimal("-76");

    @Test
    @DisplayName("el valor por defecto de application.properties es Lima, negativo")
    void porDefectoEnProperties() throws Exception {
        Properties propiedades = new Properties();
        try (InputStream entrada = getClass().getResourceAsStream("/application.properties")) {
            assertThat(entrada).as("application.properties").isNotNull();
            propiedades.load(entrada);
        }

        assertThat(defectoDe(propiedades.getProperty("compras.tienda.latitud")))
                .as("latitud por defecto").isBetween(LAT_MIN, LAT_MAX);
        assertThat(defectoDe(propiedades.getProperty("compras.tienda.longitud")))
                .as("longitud por defecto").isBetween(LON_MIN, LON_MAX);
    }

    @Test
    @DisplayName("el valor por defecto de las anotaciones @Value también es Lima")
    void porDefectoEnLasAnotaciones() throws Exception {
        assertThat(defectoDe(expresionDe("tiendaLat")))
                .as("latitud del @Value").isBetween(LAT_MIN, LAT_MAX);
        assertThat(defectoDe(expresionDe("tiendaLon")))
                .as("longitud del @Value").isBetween(LON_MIN, LON_MAX);
    }

    /** La expresión {@code ${...}} declarada en el campo de {@link EnvioService}. */
    private String expresionDe(String campo) throws NoSuchFieldException {
        Field field = EnvioService.class.getDeclaredField(campo);
        Value anotacion = field.getAnnotation(Value.class);
        assertThat(anotacion).as("@Value en %s", campo).isNotNull();
        return anotacion.value();
    }

    /**
     * Lo que hay tras el PRIMER {@code :} de un {@code ${VAR:defecto}}, que es
     * exactamente donde Spring corta. Cortar por el último se comería el signo
     * y la prueba pasaría con el fallo dentro.
     */
    private BigDecimal defectoDe(String expresion) {
        assertThat(expresion).as("expresión de propiedad").isNotNull();
        String interior = expresion.substring(expresion.indexOf('{') + 1, expresion.lastIndexOf('}'));
        int separador = interior.indexOf(':');
        assertThat(separador).as("la expresión %s no declara valor por defecto", expresion)
                .isNotNegative();
        return new BigDecimal(interior.substring(separador + 1));
    }

    @Test
    @DisplayName("desde Lima hasta una dirección de Lima salen kilómetros de ciudad")
    void distanciaRazonableDentroDeLima() {
        // Miraflores, a unos 8 km en línea recta del centro. Con el signo
        // cambiado esta misma cuenta daba más de 19 000 km.
        Distancia.Estimacion estimacion = Distancia.desdeLaTienda(
                new BigDecimal("-12.046374"), new BigDecimal("-77.042793"),
                new BigDecimal("-12.121000"), new BigDecimal("-77.030000"));

        assertThat(estimacion).isNotNull();
        assertThat(estimacion.kilometros()).isLessThan(30);
        assertThat(estimacion.minutos()).isLessThan(120);
    }
}
