package com.backend.catalogo.descubrimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Que medir no rompa nada y no delate a nadie.
 *
 * <p>Dos propiedades por encima del resto. La primera es que un cronómetro no
 * puede tragarse una excepción: el pipeline tiene un plan de vuelta atrás —el
 * ranker estable— que solo funciona si los fallos llegan a quien sabe qué hacer
 * con ellos, y una instrumentación que los absorbiera convertiría una avería en
 * un silencio. La segunda es que las etiquetas sean de conjuntos cerrados: una
 * etiqueta por producto o por persona delata conducta y, de paso, revienta la
 * cardinalidad de Prometheus.
 */
@DisplayName("Instrumentación del pipeline")
class MetricasPipelineTest {

    private final SimpleMeterRegistry registro = new SimpleMeterRegistry();
    private final MetricasPipeline cronometros = new MetricasPipeline(registro);

    @Nested
    @DisplayName("Cronómetros")
    class Cronometros {

        @Test
        @DisplayName("una etapa que devuelve algo se mide y devuelve lo suyo")
        void mideYDevuelve() {
            String salida = cronometros.etapa(MetricasPipeline.HOME, "POPULARES",
                    MetricasPipeline.ORDENAR, () -> "lo de siempre");

            assertThat(salida)
                    .as("medir no puede cambiar lo que produce la etapa")
                    .isEqualTo("lo de siempre");
            assertThat(vecesDe(MetricasPipeline.ETAPA)).isEqualTo(1);
        }

        @Test
        @DisplayName("una etapa sin resultado también se mide")
        void mideLoQueNoDevuelveNada() {
            StringBuilder efecto = new StringBuilder();
            cronometros.etapa(MetricasPipeline.HOME, "POPULARES", MetricasPipeline.ENFRIAR,
                    () -> efecto.append("hecho"));

            assertThat(efecto).hasToString("hecho");
            assertThat(vecesDe(MetricasPipeline.ETAPA)).isEqualTo(1);
        }

        @Test
        @DisplayName("el total es su propio cronómetro, no la suma de las etapas")
        void elTotalEsSuyo() {
            /*
             * Sumar etapas solo puede devolver lo que se instrumento, y lo que
             * hace falta saber es justo si queda tiempo FUERA de las etapas
             * conocidas. Por eso son dos metricas distintas.
             */
            cronometros.total(MetricasPipeline.HOME, () -> {
                cronometros.etapa(MetricasPipeline.HOME, "POPULARES",
                        MetricasPipeline.ORDENAR, () -> "x");
                return "listo";
            });

            assertThat(vecesDe(MetricasPipeline.TOTAL)).isEqualTo(1);
            assertThat(vecesDe(MetricasPipeline.ETAPA)).isEqualTo(1);
            assertThat(registro.find(MetricasPipeline.TOTAL).timer().totalTime(TimeUnit.NANOSECONDS))
                    .as("el total incluye lo que pasa entre etapas")
                    .isGreaterThanOrEqualTo(registro.find(MetricasPipeline.ETAPA).timer()
                            .totalTime(TimeUnit.NANOSECONDS));
        }
    }

    @Nested
    @DisplayName("Fallos")
    class Fallos {

        @Test
        @DisplayName("una excepción dentro de una etapa sigue su camino")
        void laExcepcionNoSeQuedaDentro() {
            /*
             * Si el cronometro se la tragara, el ranker adaptativo nunca caeria
             * al estable y el Home se serviria a medias sin que nadie se
             * enterase. La instrumentacion no puede tapar una averia.
             */
            assertThatThrownBy(() -> cronometros.etapa(MetricasPipeline.HOME, "POPULARES",
                    MetricasPipeline.ORDENAR, () -> {
                        throw new IllegalStateException("algo se rompió");
                    }))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("algo se rompió");
        }

        @Test
        @DisplayName("y la etapa queda registrada igual")
        void elCronometroSeCierraAunqueFalle() {
            try {
                cronometros.etapa(MetricasPipeline.HOME, "POPULARES", MetricasPipeline.ORDENAR,
                        () -> {
                            throw new IllegalStateException("algo se rompió");
                        });
            } catch (IllegalStateException esperada) {
                // El fallo es el escenario; lo que se comprueba es la medicion.
            }
            assertThat(vecesDe(MetricasPipeline.ETAPA))
                    .as("un fallo también cuesta tiempo, y ese tiempo se mide")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("el total se cierra aunque la operación entera falle")
        void elTotalSeCierraAunqueFalle() {
            try {
                cronometros.total(MetricasPipeline.FICHA, () -> {
                    throw new IllegalStateException("caída");
                });
            } catch (IllegalStateException esperada) {
                // idem
            }
            assertThat(vecesDe(MetricasPipeline.TOTAL)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Etiquetas")
    class Etiquetas {

        @Test
        @DisplayName("las etapas llevan superficie, módulo y etapa, y nada más")
        void lasEtiquetasSonLasEsperadas() {
            cronometros.etapa(MetricasPipeline.HOME, "POPULARES", MetricasPipeline.ORDENAR,
                    () -> "x");

            assertThat(clavesDe(MetricasPipeline.ETAPA))
                    .containsExactlyInAnyOrder("superficie", "modulo", "etapa");
        }

        @Test
        @DisplayName("las etapas comunes se marcan como tales, no se quedan sin módulo")
        void lasComunesLlevanModulo() {
            /*
             * Una etiqueta ausente y una etiqueta con valor no son lo mismo en
             * Prometheus: la primera crea una serie distinta y complica las
             * consultas. Por eso lo comun se etiqueta, no se deja en blanco.
             */
            cronometros.comun(MetricasPipeline.HOME, MetricasPipeline.PERFIL, () -> "x");

            assertThat(valorDe(MetricasPipeline.ETAPA, "modulo")).isEqualTo("COMUN");
        }

        @Test
        @DisplayName("ninguna métrica del pipeline lleva identificadores")
        void sinIdentificadores() {
            /*
             * Se comprueba sobre las CLAVES y los VALORES reales del registro, no
             * buscando la palabra «sujeto» en un texto: esa prueba tumbaria
             * nombres legitimos como `minimoSujetos` y dejaria pasar un UUID
             * escondido en un valor.
             */
            cronometros.total(MetricasPipeline.HOME, () -> "x");
            cronometros.etapa(MetricasPipeline.HOME, "POPULARES", MetricasPipeline.ORDENAR,
                    () -> "x");
            cronometros.comun(MetricasPipeline.FICHA, MetricasPipeline.PERFIL, () -> "x");
            cronometros.candidatos(MetricasPipeline.HOME, "POPULARES", 12);

            Set<String> prohibidas = Set.of("sujeto", "sujeto_id", "usuario", "usuario_id",
                    "item", "item_id", "producto", "producto_id", "sesion", "sesion_id",
                    "ip", "url", "query");

            List<Tag> todas = registro.getMeters().stream()
                    .flatMap(m -> m.getId().getTags().stream())
                    .toList();

            assertThat(todas).isNotEmpty();
            assertThat(todas).noneMatch(t -> prohibidas.contains(t.getKey().toLowerCase()));
            assertThat(todas).noneMatch(t -> t.getValue().matches(
                    "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
            assertThat(todas)
                    .as("ningún valor numérico suelto, que sería un identificador")
                    .noneMatch(t -> t.getValue().matches("\\d+"));
        }

        @Test
        @DisplayName("la cardinalidad está acotada por conjuntos cerrados")
        void cardinalidadAcotada() {
            for (ModuloDescubrimiento modulo : ModuloDescubrimiento.values()) {
                cronometros.etapa(MetricasPipeline.HOME, modulo.name(),
                        MetricasPipeline.ORDENAR, () -> "x");
            }
            assertThat(registro.find(MetricasPipeline.ETAPA).timers())
                    .as("una serie por módulo y no una por petición")
                    .hasSize(ModuloDescubrimiento.values().length);
        }
    }

    @Nested
    @DisplayName("Tamaño de lote")
    class TamanoDeLote {

        @Test
        @DisplayName("los candidatos se registran como distribución, no como etiqueta")
        void losCandidatosSonUnaDistribucion() {
            /*
             * La cuenta va en el VALOR y no en una etiqueta. Etiquetar por
             * numero de candidatos crearia una serie nueva cada vez que el
             * numero cambiase, que es la forma clasica de reventar Prometheus.
             */
            cronometros.candidatos(MetricasPipeline.HOME, "POPULARES", 12);
            cronometros.candidatos(MetricasPipeline.HOME, "POPULARES", 60);

            assertThat(registro.find(MetricasPipeline.CANDIDATOS).summaries()).hasSize(1);
            assertThat(registro.find(MetricasPipeline.CANDIDATOS).summary().count()).isEqualTo(2);
            assertThat(registro.find(MetricasPipeline.CANDIDATOS).summary().max()).isEqualTo(60);
        }
    }

    /* ══════════════ Utilidades ══════════════ */

    private long vecesDe(String metrica) {
        return registro.find(metrica).timers().stream().mapToLong(t -> t.count()).sum();
    }

    private Set<String> clavesDe(String metrica) {
        return registro.find(metrica).meters().stream()
                .map(Meter::getId)
                .flatMap(id -> id.getTags().stream())
                .map(Tag::getKey)
                .collect(java.util.stream.Collectors.toSet());
    }

    private String valorDe(String metrica, String clave) {
        return registro.find(metrica).meters().stream()
                .map(m -> m.getId().getTag(clave))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow();
    }
}
