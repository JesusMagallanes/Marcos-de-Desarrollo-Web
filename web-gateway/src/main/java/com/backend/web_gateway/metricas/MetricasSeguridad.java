package com.backend.web_gateway.metricas;

import org.springframework.stereotype.Component;

import com.backend.web_gateway.seguridad.LimitadorPeticiones;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

/** Métricas del gateway, expuestas en /actuator/prometheus. */
@Component
public class MetricasSeguridad {

    private final MeterRegistry registro;

    public MetricasSeguridad(MeterRegistry registro, LimitadorPeticiones limitador) {
        this.registro = registro;

        Gauge.builder(Metricas.RATE_LIMIT_CLAVES, limitador, LimitadorPeticiones::clavesActivas)
                .description("Claves distintas que el limitador está siguiendo")
                .register(registro);
    }

    /* ── Abuso · A04 ── */

    /** @param ambito autenticacion · pagos · escritura · lectura. */
    public void rateLimitBloqueado(String ambito) {
        contador(Metricas.RATE_LIMIT, "Peticiones rechazadas por exceder el cupo",
                Tags.of("ambito", ambito)).increment();
    }

    /* ── Tokens ── */

    /** @param motivo firma · expirado · emisor · audiencia · ausente_o_invalido. */
    public void tokenInvalido(String motivo) {
        contador(Metricas.TOKEN, "Ciclo de vida de los tokens",
                Tags.of("evento", "invalido", "motivo", motivo)).increment();
    }

    /* ── Autorización · A01 ── */

    /** @param razon sin_permiso · origen_no_permitido. */
    public void peticionBloqueada(String razon) {
        contador(Metricas.AUTORIZACION_DENEGADA, "Peticiones rechazadas antes de enrutarse",
                Tags.of("recurso", razon)).increment();
    }

    private Counter contador(String nombre, String descripcion, Tags etiquetas) {
        return Counter.builder(nombre)
                .description(descripcion)
                .tags(etiquetas)
                .register(registro);
    }
}
