package com.backend.compras.envio;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El cálculo de distancia al punto de entrega (Épica 3).
 *
 * <p>Esto se prueba porque un error aquí no da ningún síntoma: nadie se da
 * cuenta de que un reparto «a 3 km» está en realidad a 30 hasta que el
 * repartidor vuelve tarde. Los casos usan puntos reales de Lima con distancias
 * conocidas.
 */
@DisplayName("Distancia al punto de entrega")
class DistanciaTest {

    /** Plaza de Armas de Lima, el punto por defecto de la tienda. */
    private static final BigDecimal TIENDA_LAT = new BigDecimal("-12.046374");
    private static final BigDecimal TIENDA_LON = new BigDecimal("-77.042793");

    private BigDecimal g(String valor) {
        return new BigDecimal(valor);
    }

    @Test
    @DisplayName("sin coordenadas no inventa nada")
    void sinCoordenadas() {
        // El comprador no compartió su ubicación: se devuelve null y la interfaz
        // no enseña distancia. Inventar un número sería peor que no dar ninguno.
        assertThat(Distancia.desdeLaTienda(TIENDA_LAT, TIENDA_LON, null, null)).isNull();
        assertThat(Distancia.desdeLaTienda(TIENDA_LAT, TIENDA_LON, g("-12.1"), null)).isNull();
        assertThat(Distancia.desdeLaTienda(null, null, g("-12.1"), g("-77.0"))).isNull();
    }

    @Test
    @DisplayName("Miraflores está a unos 11 km del centro por calle")
    void distanciaConocida() {
        // Plaza de Armas → Parque Kennedy: 8,4 km en línea recta, que con el
        // ajuste callejero quedan cerca de 11. Coincide con lo que dice
        // cualquier mapa para ese trayecto.
        var e = Distancia.desdeLaTienda(TIENDA_LAT, TIENDA_LON, g("-12.121"), g("-77.029"));

        assertThat(e).isNotNull();
        assertThat(e.kilometros()).isBetween(10.0, 12.0);
        // A 18 km/h de media urbana, entre media hora y tres cuartos.
        assertThat(e.minutos()).isBetween(30, 45);
    }

    @Test
    @DisplayName("el mismo punto da distancia cero pero nunca cero minutos")
    void mismoPunto() {
        var e = Distancia.desdeLaTienda(TIENDA_LAT, TIENDA_LON, TIENDA_LAT, TIENDA_LON);

        assertThat(e.kilometros()).isEqualTo(0.0);
        // Un reparto nunca cuesta cero minutos: aparcar y entregar ya lleva algo,
        // y un "0 min" en la pantalla se lee como un error.
        assertThat(e.minutos()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("más lejos es más tiempo, siempre")
    void monotonia() {
        var cerca = Distancia.desdeLaTienda(TIENDA_LAT, TIENDA_LON, g("-12.06"), g("-77.05"));
        var lejos = Distancia.desdeLaTienda(TIENDA_LAT, TIENDA_LON, g("-12.20"), g("-76.95"));

        assertThat(lejos.kilometros()).isGreaterThan(cerca.kilometros());
        assertThat(lejos.minutos()).isGreaterThan(cerca.minutos());
    }

    @Test
    @DisplayName("se redondea a un decimal: no se finge precisión que no hay")
    void redondeo() {
        var e = Distancia.desdeLaTienda(TIENDA_LAT, TIENDA_LON, g("-12.121"), g("-77.029"));

        // Un "8,3 km" es honesto; un "8,34812 km" sugiere una precisión que un
        // cálculo en línea recta con un factor aproximado no tiene.
        assertThat(e.kilometros() * 10).isEqualTo(Math.round(e.kilometros() * 10));
    }

    @Test
    @DisplayName("siempre se marca como estimación")
    void siempreEsEstimacion() {
        // No es una ruta: no conoce calles, ni sentidos, ni tráfico. Que la
        // respuesta lo diga es lo que evita que alguien la tome por exacta.
        var e = Distancia.desdeLaTienda(TIENDA_LAT, TIENDA_LON, g("-12.1"), g("-77.0"));

        assertThat(e.esEstimacion()).isTrue();
    }

    @Test
    @DisplayName("cruzar el meridiano no rompe el cálculo")
    void cruceDeMeridiano() {
        // Longitudes de signo distinto: si el cálculo restara mal, saldrían
        // miles de kilómetros de más.
        var e = Distancia.desdeLaTienda(g("51.4775"), g("-0.0005"), g("51.4775"), g("0.0005"));

        assertThat(e.kilometros()).isLessThan(1.0);
    }
}
