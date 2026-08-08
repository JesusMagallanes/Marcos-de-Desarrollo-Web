package com.backend.usuarios.shared.metricas;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.backend.usuarios.shared.seguridad.LimitadorPeticiones;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Las métricas son la única señal en tiempo real de que algo va mal.
 * Si un contador deja de incrementarse, un ataque pasa inadvertido.
 */
class MetricasSeguridadTest {

    private MeterRegistry registro;
    private MetricasSeguridad metricas;

    @BeforeEach
    void preparar() {
        registro = new SimpleMeterRegistry();
        metricas = new MetricasSeguridad(registro, new LimitadorPeticiones());
    }

    private double valor(String nombre, String... etiquetas) {
        return registro.find(nombre).tags(etiquetas).counter() == null
                ? 0
                : registro.find(nombre).tags(etiquetas).counter().count();
    }

    @Test
    @DisplayName("separa autenticaciones correctas de fallidas")
    void autenticacion() {
        metricas.loginCorrecto("CLIENTE");
        metricas.loginCorrecto("ADMINISTRADOR");
        metricas.loginFallido("credenciales");

        assertThat(valor(Metricas.AUTENTICACION, "resultado", "correcto", "rol", "CLIENTE"))
                .isEqualTo(1);
        assertThat(valor(Metricas.AUTENTICACION, "resultado", "correcto", "rol", "ADMINISTRADOR"))
                .isEqualTo(1);
        assertThat(valor(Metricas.AUTENTICACION, "resultado", "fallido", "motivo", "credenciales"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("el medidor de sesiones sube al emitir y baja al revocar")
    void sesionesActivas() {
        metricas.tokenEmitido();
        metricas.tokenEmitido();
        metricas.tokenEmitido();

        assertThat(registro.find(Metricas.SESIONES_ACTIVAS).gauge().value()).isEqualTo(3);

        metricas.tokenRevocado("LOGOUT");

        assertThat(registro.find(Metricas.SESIONES_ACTIVAS).gauge().value()).isEqualTo(2);
    }

    @Test
    @DisplayName("el medidor de sesiones nunca baja de cero")
    void sesionesNoNegativas() {
        // Puede pasar tras un reinicio: se revocan tokens emitidos antes.
        metricas.tokenRevocado("ROTACION");
        metricas.tokenRevocado("ROTACION");

        assertThat(registro.find(Metricas.SESIONES_ACTIVAS).gauge().value()).isZero();
    }

    @Test
    @DisplayName("los eventos de integridad se agrupan bajo un mismo contador")
    void integridad() {
        metricas.reusoRefreshDetectado();
        metricas.integridad("pago_ajeno");

        assertThat(valor(Metricas.INTEGRIDAD, "evento", "reuso_refresh")).isEqualTo(1);
        assertThat(valor(Metricas.INTEGRIDAD, "evento", "pago_ajeno")).isEqualTo(1);
    }

    @Test
    @DisplayName("cada ámbito de rate limit cuenta por separado")
    void rateLimitPorAmbito() {
        metricas.rateLimitBloqueado("autenticacion");
        metricas.rateLimitBloqueado("autenticacion");
        metricas.rateLimitBloqueado("lectura");

        assertThat(valor(Metricas.RATE_LIMIT, "ambito", "autenticacion")).isEqualTo(2);
        assertThat(valor(Metricas.RATE_LIMIT, "ambito", "lectura")).isEqualTo(1);
    }

    @Test
    @DisplayName("los nombres siguen la convención de Prometheus")
    void convencionDeNombres() {
        String[] contadores = {
                Metricas.AUTENTICACION, Metricas.TOKEN, Metricas.AUTORIZACION_DENEGADA,
                Metricas.CAMBIO_ROL, Metricas.RATE_LIMIT, Metricas.ENTRADA_RECHAZADA,
                Metricas.INTEGRIDAD, Metricas.SAGA,
        };

        for (String nombre : contadores) {
            assertThat(nombre)
                    .as("los contadores terminan en _total")
                    .endsWith("_total");
            assertThat(nombre)
                    .as("prefijo común para no chocar con métricas de la JVM")
                    .startsWith("smartzone_");
            assertThat(nombre)
                    .as("solo minúsculas y guiones bajos")
                    .matches("^[a-z_]+$");
        }

        // Los medidores NO llevan sufijo _total.
        assertThat(Metricas.SESIONES_ACTIVAS).startsWith("smartzone_").doesNotEndWith("_total");
        assertThat(Metricas.RATE_LIMIT_CLAVES).startsWith("smartzone_").doesNotEndWith("_total");
    }

    @Test
    @DisplayName("las etiquetas son de cardinalidad baja: nada de correos ni IPs")
    void cardinalidadBaja() {
        metricas.loginFallido("credenciales");
        metricas.accesoDenegado("usuarios");

        // Si alguien colara un correo o una IP como etiqueta, cada valor
        // distinto crearía una serie temporal nueva y tumbaría al recolector.
        registro.getMeters().forEach(medidor ->
                medidor.getId().getTags().forEach(etiqueta -> {
                    assertThat(etiqueta.getValue())
                            .as("etiqueta '%s' no debe contener un correo", etiqueta.getKey())
                            .doesNotContain("@");
                    assertThat(etiqueta.getValue())
                            .as("etiqueta '%s' no debe parecer una IP", etiqueta.getKey())
                            .doesNotMatch("^\\d+\\.\\d+\\.\\d+\\.\\d+$");
                }));
    }
}
