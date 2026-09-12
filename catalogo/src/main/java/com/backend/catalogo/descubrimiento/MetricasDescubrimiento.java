package com.backend.catalogo.descubrimiento;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

/**
 * Lo que hay que poder mirar del recomendador sin abrir la base de datos.
 *
 * <p>Un recomendador que no se mide es una superstición: acierta o no acierta y
 * nadie lo sabe hasta que las ventas bajan. Las cuentas de aquí responden las
 * tres preguntas que de verdad se hacen cuando algo va mal: ¿está el proceso por
 * lotes produciendo relaciones o se quedó a cero? ¿qué proporción de visitantes
 * llega sin nada personal que ofrecerles? ¿qué generador está sosteniendo el
 * Home?
 *
 * <h4>Qué NO se registra</h4>
 *
 * <p>Ningún identificador: ni sujeto, ni usuario, ni producto. Todo son cuentas
 * agregadas y etiquetas de un conjunto cerrado y pequeño —el nombre de la razón,
 * que es un enum—. Una métrica etiquetada por producto, además de filtrar
 * conducta, revienta la cardinalidad de Prometheus, que es como se tira un
 * sistema de monitorización sin querer.
 */
@Component
public class MetricasDescubrimiento {

    /** Relaciones producto↔producto vivas tras la última pasada del lote. */
    public static final String RELACIONES_ITEM = "smartzone_descubrimiento_relaciones_item";

    /** Aristas de parecido entre perfiles vivas tras la última pasada. */
    public static final String RELACIONES_SUJETO = "smartzone_descubrimiento_relaciones_sujeto";

    /** Candidatos colaborativos propuestos, por razón. */
    public static final String CANDIDATOS = "smartzone_descubrimiento_candidatos_total";

    /** Homes servidos, separando los que no tenían nada personal que dar. */
    public static final String HOME = "smartzone_descubrimiento_home_total";

    /** Impresiones declaradas que no correspondian a nada servido. */
    public static final String IMPRESIONES_DESCARTADAS =
            "smartzone_descubrimiento_impresiones_descartadas_total";

    /** Recomendaciones anotadas para poder evaluarlas después, por razón. */
    public static final String SERVIDAS = "smartzone_descubrimiento_servidas_total";

    /** Filas del agregado diario que dejó la última pasada de medición. */
    public static final String METRICAS_AGREGADAS =
            "smartzone_descubrimiento_metricas_agregadas";

    private final MeterRegistry registro;

    private final AtomicLong relacionesItem = new AtomicLong();
    private final AtomicLong relacionesSujeto = new AtomicLong();
    private final AtomicLong metricasAgregadas = new AtomicLong();

    public MetricasDescubrimiento(MeterRegistry registro) {
        this.registro = registro;

        Gauge.builder(RELACIONES_ITEM, relacionesItem, AtomicLong::get)
                .description("Relaciones entre productos vivas")
                .register(registro);
        Gauge.builder(RELACIONES_SUJETO, relacionesSujeto, AtomicLong::get)
                .description("Aristas de parecido entre perfiles vivas")
                .register(registro);
        Gauge.builder(METRICAS_AGREGADAS, metricasAgregadas, AtomicLong::get)
                .description("Filas del agregado diario de la ultima medicion")
                .register(registro);
    }

    /**
     * Lo que anotó una pasada del registro, por razón.
     *
     * <p>Contra los clics que reciba después, es lo que dice qué generador
     * merece más peso. Etiquetado solo por la razón —un enum de siete valores—:
     * etiquetar por producto filtraría conducta y además reventaría la
     * cardinalidad de Prometheus, que es como se tumba un sistema de
     * monitorización sin querer.
     */
    public void recomendacionesServidas(RazonRecomendacion razon, int cuantas) {
        if (cuantas <= 0) {
            return;
        }
        Counter.builder(SERVIDAS)
                .description("Recomendaciones anotadas para evaluacion posterior")
                .tags(Tags.of("razon", razon.name()))
                .register(registro)
                .increment(cuantas);
    }

    /** Lo que dejó la última pasada de la medición. Un cero sostenido es una avería. */
    public void medicionTerminada(long filasAgregadas) {
        metricasAgregadas.set(filasAgregadas);
    }

    /**
     * Impresiones que el cliente declaró y el servidor no pudo sostener.
     *
     * <p>Agregado y sin identificadores: cuántas, no de quién ni de qué
     * producto. Un cliente legítimo produce cero, porque solo declara lo que se
     * le sirvió; un valor sostenido aquí es la señal de que alguien está
     * intentando fabricar exposición.
     */
    public void impresionesDescartadas(int cuantas) {
        if (cuantas <= 0) {
            return;
        }
        registro.counter(IMPRESIONES_DESCARTADAS).increment(cuantas);
    }

    /** Lo que dejó la última pasada del proceso por lotes. */
    public void loteTerminado(long relacionesEntreItems, long relacionesEntreSujetos) {
        relacionesItem.set(relacionesEntreItems);
        relacionesSujeto.set(relacionesEntreSujetos);
    }

    /**
     * Cuántos candidatos propuso cada generador.
     *
     * <p>Comparado con los clics que reciben, es lo que dice qué señal merece más
     * peso. Sin esto los pesos de {@code PesosDescubrimiento} se ajustan a ojo.
     */
    public void candidatosGenerados(RazonRecomendacion razon, int cuantos) {
        if (cuantos <= 0) {
            return;
        }
        Counter.builder(CANDIDATOS)
                .description("Candidatos propuestos por cada generador")
                .tags(Tags.of("razon", razon.name()))
                .register(registro)
                .increment(cuantos);
    }

    /**
     * Un Home servido.
     *
     * @param conPerfil si había perfil del que tirar; lo contrario es arranque
     *     en frío, y su proporción es la métrica que dice si el sistema está
     *     aprendiendo de la gente o solo enseñando lo más popular a todos
     * @param conColaborativo si llegó a haber carrusel colaborativo
     */
    public void homeServido(boolean conPerfil, boolean conColaborativo) {
        Counter.builder(HOME)
                .description("Homes servidos por tipo de evidencia disponible")
                .tags(Tags.of("perfil", String.valueOf(conPerfil),
                        "colaborativo", String.valueOf(conColaborativo)))
                .register(registro)
                .increment();
    }
}
