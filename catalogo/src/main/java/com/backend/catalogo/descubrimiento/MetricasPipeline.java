package com.backend.catalogo.descubrimiento;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

/**
 * Cuánto cuesta cada paso de armar una recomendación.
 *
 * <h4>Qué faltaba</h4>
 *
 * <p>Había tres cronómetros sueltos —elegibilidad, sesión y enfriamiento— que
 * llegaron con los bloques A, C y D porque cada uno quiso justificar su propio
 * coste. El corazón del pipeline no se medía: ni la generación de candidatos, ni
 * las tres etapas del ranker, ni la diversificación, ni el armado final. Con eso
 * no se puede responder dónde está el cuello de botella; solo se puede opinar.
 *
 * <h4>El Home no es una tubería</h4>
 *
 * <p>Es una etapa común y después SEIS ciclos completos, uno por carrusel. Por
 * eso la unidad de medida es {@code (superficie, módulo, etapa)} y no una lista
 * de fases globales: un tiempo total por etapa sumaría seis módulos distintos y
 * escondería exactamente lo que se busca, que es cuál de ellos cuesta.
 *
 * <h4>Cardinalidad</h4>
 *
 * <p>Dos superficies, seis módulos y una docena larga de etapas: unas decenas de
 * series, todas de conjuntos cerrados —enumerados o constantes de esta clase—.
 * Ni un identificador de persona, de producto o de sesión entra aquí como
 * etiqueta: además de delatar conducta, una etiqueta por producto revienta la
 * cardinalidad de Prometheus, que es como se tumba la monitorización sin querer.
 *
 * <h4>Medir no puede cambiar el resultado</h4>
 *
 * <p>Los envoltorios devuelven lo que devuelve la operación y dejan pasar
 * cualquier excepción tal cual. Un cronómetro que se tragara un fallo
 * convertiría la observabilidad en una avería silenciosa, y en este pipeline
 * hay un plan de vuelta atrás —el ranker estable— que depende de que las
 * excepciones lleguen a quien sabe qué hacer con ellas.
 */
@Component
public class MetricasPipeline {

    /** Lo que tarda una etapa del pipeline. Etiquetado por superficie y módulo. */
    public static final String ETAPA = "smartzone_descubrimiento_etapa_segundos";

    /** Lo que tarda la operación entera, medida de verdad y no sumando etapas. */
    public static final String TOTAL = "smartzone_descubrimiento_total_segundos";

    /** Cuántos candidatos produjo un generador. Para ver crecimientos raros. */
    public static final String CANDIDATOS = "smartzone_descubrimiento_candidatos_lote";

    /* ── Superficies ── */
    public static final String HOME = "HOME";
    public static final String FICHA = "FICHA";

    /* ── Etapas comunes ── */
    public static final String PERFIL = "perfil";
    public static final String EXCLUSIONES = "exclusiones";
    public static final String CANDIDATOS_SQL = "candidatos";
    public static final String ENFRIAR = "enfriar";
    public static final String ORDENAR = "ordenar";
    public static final String DIVERSIFICAR = "diversificar";
    public static final String ANOTAR = "anotar";
    public static final String POR_IDS = "porIds";

    /* ── Etapas del ranker ── */
    public static final String EXTRAER = "ranker.extraer";
    public static final String EXPOSICION = "ranker.extraer.sql";
    public static final String PUNTUAR = "ranker.puntuar";
    public static final String EXPLORAR = "ranker.explorar";

    /* ── Etapas propias de la ficha ── */
    public static final String CONTENIDO = "similares.contenido";
    public static final String CO_VISITA = "similares.covisita";
    public static final String FILTRO = "filtro";

    /** Cuando una etapa no pertenece a ningún carrusel concreto. */
    private static final String SIN_MODULO = "COMUN";

    private final MeterRegistry registro;

    public MetricasPipeline(MeterRegistry registro) {
        this.registro = registro;
    }

    /**
     * Cronometra una etapa que devuelve algo.
     *
     * <p>Si la operación lanza, el cronómetro se cierra igual —Micrometer lo
     * hace en su propio {@code finally}— y la excepción sigue su camino. Es lo
     * que permite que el ranker adaptativo caiga al estable sin que la medición
     * se entere ni estorbe.
     */
    public <T> T etapa(String superficie, String modulo, String etapa, Supplier<T> operacion) {
        return cronometro(superficie, modulo, etapa).record(operacion);
    }

    /** La misma, para una etapa que no devuelve nada. */
    public void etapa(String superficie, String modulo, String etapa, Runnable operacion) {
        cronometro(superficie, modulo, etapa).record(operacion);
    }

    /** Una etapa que no pertenece a ningún carrusel: perfil, sesión, enfriamiento. */
    public <T> T comun(String superficie, String etapa, Supplier<T> operacion) {
        return etapa(superficie, SIN_MODULO, etapa, operacion);
    }

    /**
     * Cronometra la operación COMPLETA.
     *
     * <p>Medida de verdad y no como suma de sus etapas: sumar solo puede dar lo
     * que se instrumentó, y justamente lo que interesa saber es si queda tiempo
     * fuera de las etapas conocidas.
     */
    public <T> T total(String superficie, Supplier<T> operacion) {
        return Timer.builder(TOTAL)
                .description("Tiempo de la operacion de descubrimiento entera")
                .tags(Tags.of("superficie", superficie))
                .register(registro)
                .record(operacion);
    }

    /** Cuántos candidatos trajo un generador. Un salto aquí precede a uno de latencia. */
    public void candidatos(String superficie, String modulo, int cuantos) {
        DistributionSummary.builder(CANDIDATOS)
                .description("Candidatos producidos por un generador")
                .tags(Tags.of("superficie", superficie, "modulo", modulo))
                .register(registro)
                .record(cuantos);
    }

    private Timer cronometro(String superficie, String modulo, String etapa) {
        return Timer.builder(ETAPA)
                .description("Tiempo de una etapa del pipeline de descubrimiento")
                .tags(Tags.of("superficie", superficie, "modulo", modulo, "etapa", etapa))
                .register(registro);
    }
}
