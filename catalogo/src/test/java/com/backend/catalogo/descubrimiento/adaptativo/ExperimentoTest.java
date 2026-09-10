package com.backend.catalogo.descubrimiento.adaptativo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.backend.catalogo.descubrimiento.adaptativo.AsignacionExperimento.Variante;
import com.backend.catalogo.descubrimiento.adaptativo.Guardarrailes.Veredicto;
import com.backend.catalogo.descubrimiento.evaluacion.ResultadoEvaluacion;

/**
 * El reparto en variantes y lo que hace falta para declarar una ganadora.
 *
 * <p>Las dos mitades protegen del mismo error por sitios distintos. La
 * asignación evita medir mal —una persona que ve las dos variantes convierte
 * cualquier cifra en ruido— y los guardarraíles evitan concluir mal, que es
 * peor, porque una conclusión equivocada se despliega.
 */
@DisplayName("Experimentación")
class ExperimentoTest {

    private PesosAdaptativos pesos;
    private AsignacionExperimento asignacion;
    private Guardarrailes guardarrailes;

    @BeforeEach
    void preparar() {
        pesos = new PesosAdaptativos();
        pesos.setPorcentajeVariante(50);
        asignacion = new AsignacionExperimento(pesos);
        guardarrailes = new Guardarrailes();
    }

    /* ══════════════ Asignación ══════════════ */

    @Test
    @DisplayName("el mismo sujeto recibe siempre la misma variante")
    void laAsignacionEsEstable() {
        /*
         * Si variara entre peticiones, la misma persona vería una mezcla de las
         * dos calibraciones y ninguna medida significaría nada. Es el motivo de
         * que esto sea una función y no un `random`.
         */
        UUID sujeto = UUID.randomUUID();
        Variante primera = asignacion.de(sujeto);

        for (int i = 0; i < 50; i++) {
            assertThat(asignacion.de(sujeto)).isEqualTo(primera);
        }
    }

    @Test
    @DisplayName("el reparto se acerca al porcentaje pedido")
    void elRepartoEsUniforme() {
        int variantes = 0;
        int total = 4000;
        for (int i = 0; i < total; i++) {
            if (asignacion.de(UUID.randomUUID()) == Variante.VARIANTE) {
                variantes++;
            }
        }
        // Margen amplio: se comprueba que reparte, no que sea un generador
        // criptográfico. Un sesgo grave se vería igual.
        assertThat((double) variantes / total).isBetween(0.45, 0.55);
    }

    @Test
    @DisplayName("cambiar de experimento vuelve a barajar")
    void otroExperimentoRepartDistinto() {
        /*
         * Sin esto, los mismos sujetos caerían siempre del mismo lado y
         * cualquier sesgo de ese grupo contaminaría todos los experimentos
         * seguidos, que es una forma silenciosa de equivocarse durante meses.
         */
        Set<UUID> sujetos = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            sujetos.add(UUID.randomUUID());
        }

        Set<UUID> conPrimero = new HashSet<>();
        sujetos.forEach(s -> {
            if (asignacion.de(s) == Variante.VARIANTE) {
                conPrimero.add(s);
            }
        });

        pesos.setExperimento("v4-siguiente");
        Set<UUID> conSegundo = new HashSet<>();
        sujetos.forEach(s -> {
            if (asignacion.de(s) == Variante.VARIANTE) {
                conSegundo.add(s);
            }
        });

        assertThat(conSegundo)
                .as("el grupo tiene que cambiar, no ser el mismo de siempre")
                .isNotEqualTo(conPrimero);
    }

    @Test
    @DisplayName("un sujeto nuevo puede caer en otra variante")
    void alCerrarSesionNoSeHeredaLaVariante() {
        /*
         * No hace falta borrar nada: como la variante se calcula del
         * identificador, el sujeto que estrena el navegador tras cerrar sesión
         * recibe la suya. La protección contra herencia de identidad de la fase 1
         * sirve aquí tal cual.
         */
        Set<Variante> vistas = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            vistas.add(asignacion.de(UUID.randomUUID()));
        }
        assertThat(vistas)
                .as("sujetos distintos, variantes distintas")
                .containsExactlyInAnyOrder(Variante.CONTROL, Variante.VARIANTE);
    }

    @Test
    @DisplayName("sin sujeto se sirve el control")
    void sinSujetoNoSeExperimenta() {
        // No hay a quién atribuir el resultado, así que meterlo en el
        // experimento solo ensuciaría la medida.
        assertThat(asignacion.de(null)).isEqualTo(Variante.CONTROL);
    }

    @Test
    @DisplayName("con el experimento al 0 % nadie recibe la variante")
    void elExperimentoSeApaga() {
        pesos.setPorcentajeVariante(0);
        for (int i = 0; i < 100; i++) {
            assertThat(asignacion.de(UUID.randomUUID())).isEqualTo(Variante.CONTROL);
        }
    }

    /* ══════════════ Guardarraíles ══════════════ */

    private ResultadoEvaluacion resultado(String nombre, int sujetos, double ndcg,
            double cobertura, double diversidad, double novedad, double repeticion) {
        return new ResultadoEvaluacion(nombre, sujetos, ndcg, ndcg, ndcg, ndcg,
                cobertura, diversidad, diversidad, novedad, repeticion, 0.5, Map.of());
    }

    @Test
    @DisplayName("subir el CTR destrozando la cobertura no gana")
    void unaVarianteMiopeNoSePromueve() {
        /*
         * El escenario que estos límites existen para frenar. Una lista de diez
         * superventas acierta más y deja el 95 % del catálogo invisible: el panel
         * enseña una curva que sube mientras la tienda se estrecha.
         */
        var control = resultado("control", 1000, 0.30, 0.50, 0.60, 0.70, 0.0);
        var variante = resultado("variante", 1000, 0.45, 0.20, 0.30, 0.30, 0.0);

        Guardarrailes.Dictamen dictamen = guardarrailes.evaluar(control, variante);

        assertThat(dictamen.veredicto()).isEqualTo(Veredicto.NO_PROMOVER);
        assertThat(dictamen.motivos())
                .as("y se dice exactamente qué se rompió")
                .anyMatch(m -> m.contains("cobertura"));
    }

    @Test
    @DisplayName("mejorar sin romper nada sí gana")
    void unaVarianteSanaSePromueve() {
        var control = resultado("control", 1000, 0.30, 0.50, 0.60, 0.70, 0.0);
        var variante = resultado("variante", 1000, 0.34, 0.53, 0.62, 0.72, 0.0);

        assertThat(guardarrailes.evaluar(control, variante).promovible()).isTrue();
    }

    @Test
    @DisplayName("con poca muestra el resultado es inconcluso, no un empate")
    void muestraInsuficienteEsInconcluso() {
        /*
         * La distinción importa. «Inconcluso» dice que hay que seguir midiendo;
         * «no gana» dice que la variante es peor. Confundirlos hace que se
         * descarten buenas ideas por falta de datos.
         */
        var control = resultado("control", 20, 0.30, 0.50, 0.60, 0.70, 0.0);
        var variante = resultado("variante", 18, 0.90, 0.90, 0.90, 0.90, 0.0);

        Guardarrailes.Dictamen dictamen = guardarrailes.evaluar(control, variante);

        assertThat(dictamen.veredicto()).isEqualTo(Veredicto.INCONCLUSIVO);
        assertThat(dictamen.promovible()).isFalse();
        assertThat(dictamen.motivos()).anyMatch(m -> m.contains("muestra insuficiente"));
    }

    @Test
    @DisplayName("mejorar de más no basta si repite lo ya visto")
    void laRepeticionDescalifica() {
        var control = resultado("control", 1000, 0.30, 0.50, 0.60, 0.70, 0.0);
        var variante = resultado("variante", 1000, 0.50, 0.55, 0.65, 0.75, 0.30);

        Guardarrailes.Dictamen dictamen = guardarrailes.evaluar(control, variante);

        assertThat(dictamen.veredicto()).isEqualTo(Veredicto.NO_PROMOVER);
        assertThat(dictamen.motivos()).anyMatch(m -> m.contains("repetición"));
    }

    @Test
    @DisplayName("una mejora insignificante no justifica el cambio")
    void unaMejoraMinusculaNoSePromueve() {
        var control = resultado("control", 1000, 0.300, 0.50, 0.60, 0.70, 0.0);
        var variante = resultado("variante", 1000, 0.301, 0.50, 0.60, 0.70, 0.0);

        assertThat(guardarrailes.evaluar(control, variante).veredicto())
                .as("cambiar la calibración tiene un coste; una milésima no lo paga")
                .isEqualTo(Veredicto.NO_PROMOVER);
    }

    @Test
    @DisplayName("el dictamen siempre explica por qué")
    void siempreHayMotivos() {
        var control = resultado("control", 1000, 0.30, 0.50, 0.60, 0.70, 0.0);
        var buena = resultado("buena", 1000, 0.34, 0.53, 0.62, 0.72, 0.0);

        assertThat(guardarrailes.evaluar(control, buena).motivos())
                .as("también cuando gana: un sí sin motivo no se puede revisar")
                .isNotEmpty();
    }
}
