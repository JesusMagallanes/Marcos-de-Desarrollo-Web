package com.backend.catalogo.descubrimiento;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Que lo que se publica se publica, y que no lleva nada dentro.
 *
 * <p>La segunda mitad es la que importa. Una métrica etiquetada por producto o
 * por sujeto hace dos daños a la vez: filtra conducta individual a cualquiera
 * que lea el panel, y revienta la cardinalidad de Prometheus, que es como se
 * tira un sistema de monitorización sin querer. El endpoint está autenticado,
 * pero eso protege de fuera; esto protege de dentro.
 */
@DisplayName("Métricas de descubrimiento")
class MetricasDescubrimientoTest {

    private SimpleMeterRegistry registro;
    private MetricasDescubrimiento metricas;

    @BeforeEach
    void preparar() {
        registro = new SimpleMeterRegistry();
        metricas = new MetricasDescubrimiento(registro);
    }

    @Test
    @DisplayName("los medidores del lote existen desde el arranque")
    void losMedidoresSeRegistranAlConstruir() {
        /*
         * Registrados en el constructor y no al primer uso, a propósito: un
         * medidor que solo aparece cuando ya hay datos no sirve para detectar
         * que NO los hay, que es justo la alarma que se quiere.
         */
        assertThat(registro.find(MetricasDescubrimiento.RELACIONES_ITEM).gauge()).isNotNull();
        assertThat(registro.find(MetricasDescubrimiento.RELACIONES_SUJETO).gauge()).isNotNull();
    }

    @Test
    @DisplayName("el lote publica lo que dejó")
    void elLotePublicaSusCuentas() {
        metricas.loteTerminado(120, 340);

        assertThat(registro.find(MetricasDescubrimiento.RELACIONES_ITEM).gauge().value())
                .isEqualTo(120.0);
        assertThat(registro.find(MetricasDescubrimiento.RELACIONES_SUJETO).gauge().value())
                .isEqualTo(340.0);
    }

    @Test
    @DisplayName("los candidatos se cuentan separados por generador")
    void losCandidatosSeSeparanPorRazon() {
        metricas.candidatosGenerados(RazonRecomendacion.CO_VIEWED, 12);
        metricas.candidatosGenerados(RazonRecomendacion.SIMILAR_SUBJECT, 5);
        metricas.candidatosGenerados(RazonRecomendacion.CO_VIEWED, 3);

        assertThat(registro.find(MetricasDescubrimiento.CANDIDATOS)
                .tag("razon", "CO_VIEWED").counter().count()).isEqualTo(15.0);
        assertThat(registro.find(MetricasDescubrimiento.CANDIDATOS)
                .tag("razon", "SIMILAR_SUBJECT").counter().count()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("un generador que no devolvió nada no crea serie")
    void elCeroNoRegistra() {
        metricas.candidatosGenerados(RazonRecomendacion.CO_VIEWED, 0);

        assertThat(registro.find(MetricasDescubrimiento.CANDIDATOS).counter()).isNull();
    }

    @Test
    @DisplayName("ninguna etiqueta lleva un identificador")
    void lasEtiquetasSonDeConjuntoCerrado() {
        metricas.loteTerminado(1, 1);
        metricas.candidatosGenerados(RazonRecomendacion.SIMILAR_SUBJECT, 4);
        metricas.homeServido(true, true);
        metricas.homeServido(false, false);

        /*
         * Se recorren TODAS las etiquetas publicadas y se exige que cada valor
         * pertenezca a un conjunto pequeño y conocido. Un UUID, un id de
         * producto o un correo fallarían aquí sin que nadie tenga que acordarse
         * de comprobarlo al añadir una métrica nueva.
         */
        for (Meter medidor : registro.getMeters()) {
            medidor.getId().getTags().forEach(etiqueta -> {
                assertThat(etiqueta.getKey())
                        .as("clave de etiqueta publicada")
                        .isIn("razon", "perfil", "colaborativo");

                if ("razon".equals(etiqueta.getKey())) {
                    assertThat(RazonRecomendacion.valueOf(etiqueta.getValue())).isNotNull();
                } else {
                    assertThat(etiqueta.getValue()).isIn("true", "false");
                }
            });
        }
    }
}
