package com.backend.catalogo.descubrimiento.adaptativo;

import org.springframework.stereotype.Component;

import com.backend.catalogo.descubrimiento.adaptativo.AsignacionExperimento.Variante;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

/**
 * Lo que hay que poder mirar del ranker adaptativo mientras sirve.
 *
 * <p>Las métricas de calidad —CTR, NDCG, cobertura— ya existen desde la fase 3 y
 * no se duplican aquí: se sacan del agregado diario partiendo por versión de
 * ranker. Lo que falta es lo que SOLO se sabe en el momento de decidir: qué
 * variante le tocó a cada petición, cuánto se exploró, y —la más importante—
 * cuántas veces hubo que caer al ranker estable.
 *
 * <p>Esa última es la que avisa de que algo va mal. Un sistema que cae al
 * fallback y sigue respondiendo es un sistema que funciona y que está roto a la
 * vez, y sin este contador nadie se enteraría hasta que alguien notara que las
 * recomendaciones son las de antes.
 *
 * <p>Etiquetas de conjunto cerrado y pequeño: variante y contexto. Ningún
 * identificador de nadie.
 */
@Component
public class MetricasAdaptativas {

    /** Peticiones servidas por cada variante y contexto. */
    public static final String VARIANTE = "smartzone_descubrimiento_variante_total";

    /** Huecos reservados a explorar. */
    public static final String EXPLORACION = "smartzone_descubrimiento_exploracion_total";

    /** Veces que el adaptativo no pudo y respondió el estable. */
    public static final String CAIDA_ESTABLE = "smartzone_descubrimiento_caida_estable_total";

    private final MeterRegistry registro;

    public MetricasAdaptativas(MeterRegistry registro) {
        this.registro = registro;
    }

    public void varianteServida(Variante variante, ContextoRanking contexto) {
        Counter.builder(VARIANTE)
                .description("Peticiones de ranking por variante y contexto")
                .tags(Tags.of("variante", variante.name(), "contexto", contexto.clave()))
                .register(registro)
                .increment();
    }

    public void exploracionAplicada(ContextoRanking contexto, int huecos) {
        if (huecos <= 0) {
            return;
        }
        Counter.builder(EXPLORACION)
                .description("Huecos reservados a exploracion")
                .tags(Tags.of("contexto", contexto.clave()))
                .register(registro)
                .increment(huecos);
    }

    /**
     * El contador que hay que vigilar.
     *
     * <p>Si sube, el ranker adaptativo no está sirviendo aunque la tienda
     * funcione: las recomendaciones son las de la fase 3 y las métricas de la
     * variante no significan nada.
     */
    public void caidaAlEstable() {
        Counter.builder(CAIDA_ESTABLE)
                .description("Veces que el ranker adaptativo cayo al estable")
                .register(registro)
                .increment();
    }
}
