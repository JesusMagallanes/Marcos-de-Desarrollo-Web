package com.backend.compras.shared.metricas;

import org.springframework.stereotype.Component;

import com.backend.compras.shared.seguridad.LimitadorPeticiones;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

/** Métricas de seguridad, expuestas en /actuator/prometheus. */
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

    public void rateLimitBloqueado(String ambito) {
        contador(Metricas.RATE_LIMIT, "Peticiones rechazadas por exceder el cupo",
                Tags.of("ambito", ambito)).increment();
    }

    /* ── Autorización · A01 ── */

    /** @param recurso nombre del recurso, nunca su id. */
    public void accesoDenegado(String recurso) {
        contador(Metricas.AUTORIZACION_DENEGADA, "Peticiones rechazadas por falta de permisos",
                Tags.of("recurso", recurso)).increment();
    }

    /* ── Entrada · A03 ── */

    /** @param tipo cuerpo · parametro · json · tipo · parametro_faltante. */
    public void entradaRechazada(String tipo) {
        contador(Metricas.ENTRADA_RECHAZADA, "Peticiones rechazadas por validación",
                Tags.of("tipo", tipo)).increment();
    }

    /* ── Tokens ── */

    /** @param motivo firma · expirado · emisor · audiencia · ausente_o_invalido. */
    public void tokenInvalido(String motivo) {
        contador(Metricas.TOKEN, "Ciclo de vida de los tokens",
                Tags.of("evento", "invalido", "motivo", motivo)).increment();
    }

    /* ── Integridad · A08 ── */

    /**
     * Eventos que delatan manipulación. Cualquier valor distinto de cero aquí merece
     * revisión manual.
     *
     * @param evento webhook_invalido · importe_inconsistente · pago_ajeno
     */
    public void integridad(String evento) {
        contador(Metricas.INTEGRIDAD, "Eventos que delatan manipulación",
                Tags.of("evento", evento)).increment();
    }

    /* ── Saga de compra ── */

    /** @param resultado completada · compensada · fallida. */
    public void sagaFinalizada(String resultado) {
        contador(Metricas.SAGA, "Sagas de compra finalizadas",
                Tags.of("resultado", resultado)).increment();
    }

    /** Un pago cuyo importe no coincide con el calculado por el servidor. */
    public void importeInconsistente() {
        integridad("importe_inconsistente");
    }

    /** Webhook con firma inválida: alguien intenta falsificar una notificación. */
    public void webhookRechazado() {
        integridad("webhook_invalido");
    }

    private Counter contador(String nombre, String descripcion, Tags etiquetas) {
        return Counter.builder(nombre)
                .description(descripcion)
                .tags(etiquetas)
                .register(registro);
    }
}
