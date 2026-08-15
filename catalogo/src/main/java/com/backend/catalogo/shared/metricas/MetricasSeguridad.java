package com.backend.catalogo.shared.metricas;

import org.springframework.stereotype.Component;

import com.backend.catalogo.shared.seguridad.LimitadorPeticiones;

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

    /** @param ambito chatbot · inventario · escritura · lectura. */
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

    /* ── Moderación de contenido ── */

    /**
     * Productos de colaborador revisados.
     *
     * @param resultado aprobado | rechazado
     */
    public void moderacionProducto(String resultado) {
        contador(Metricas.MODERACION, "Productos de colaborador revisados",
                Tags.of("resultado", resultado)).increment();
    }

    private Counter contador(String nombre, String descripcion, Tags etiquetas) {
        return Counter.builder(nombre)
                .description(descripcion)
                .tags(etiquetas)
                .register(registro);
    }
}
