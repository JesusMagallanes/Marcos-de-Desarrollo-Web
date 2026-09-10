package com.backend.catalogo.descubrimiento.adaptativo;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.catalogo.descubrimiento.Origen;
import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;
import com.backend.catalogo.descubrimiento.evaluacion.ConfiguracionRanker;
import com.backend.catalogo.descubrimiento.evaluacion.EvaluacionOfflineService;
import com.backend.catalogo.descubrimiento.evaluacion.ResultadoEvaluacion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Elige una calibración con datos en vez de con intuición.
 *
 * <h4>Tres ventanas, no dos</h4>
 *
 * <p>Es lo único que separa esto de un autoengaño elaborado. Si se prueban
 * cuatro configuraciones sobre una ventana y se elige la mejor, ese resultado ya
 * no es una medida: es el máximo de cuatro intentos sobre los mismos datos, y
 * saldrá optimista aunque las cuatro fueran igual de buenas. Cuantas más se
 * prueben, más optimista.
 *
 * <pre>
 *   ENTRENAMIENTO → VALIDACIÓN → PRUEBA
 *     construir       elegir      medir
 * </pre>
 *
 * <p>Se elige mirando VALIDACIÓN y se informa mirando PRUEBA, que no se ha usado
 * para nada hasta ese momento. El número que sale de ahí es el único que se
 * puede enseñar sin matices.
 *
 * <h4>Un espacio pequeño</h4>
 *
 * <p>Cuatro candidatas y no cuatrocientas. Con búsqueda masiva sobre el tráfico
 * que hay hoy, lo que se encuentra es el ruido de la ventana de validación, no
 * una calibración mejor. Cuatro hipótesis razonadas se pueden además explicar,
 * que es lo que permite entender por qué ganó la que ganó.
 *
 * <p>Nada de esto corre en una petición. Se llama desde una prueba o desde una
 * tarea, sobre ventanas acotadas.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BusquedaConfiguraciones {

    private final EvaluacionOfflineService evaluador;
    private final PesosDescubrimiento pesos;

    /**
     * @param elegida la que ganó en validación
     * @param enValidacion cómo quedaron todas al elegir; se devuelve entera para
     *     que se pueda ver el margen y no solo el nombre del ganador
     * @param enPrueba la elegida, medida sobre datos que no participaron en la
     *     elección: es la cifra que se puede citar
     */
    public record Resultado(ConfiguracionRanker elegida,
            List<ResultadoEvaluacion> enValidacion,
            ResultadoEvaluacion enPrueba) {
    }

    /**
     * Busca la mejor calibración respetando la separación de ventanas.
     *
     * @param corte frontera entre construir y comprobar
     * @param entrenamiento cuánto pasado alimenta las señales
     * @param validacion ventana para ELEGIR
     * @param prueba ventana para MEDIR, posterior a la de validación
     */
    @Transactional(readOnly = true)
    public Resultado buscar(Instant corte, Duration entrenamiento,
            Duration validacion, Duration prueba) {

        List<ConfiguracionRanker> candidatas = candidatas();

        List<ResultadoEvaluacion> enValidacion =
                evaluador.comparar(candidatas, corte, entrenamiento, validacion);

        ResultadoEvaluacion mejor = enValidacion.stream()
                .max(Comparator.comparingDouble(ResultadoEvaluacion::indiceGlobal))
                .orElseThrow();

        ConfiguracionRanker elegida = candidatas.stream()
                .filter(c -> c.nombre().equals(mejor.configuracion()))
                .findFirst()
                .orElseThrow();

        /*
         * La medida final va sobre la ventana SIGUIENTE a la de validación, que
         * no ha intervenido en la elección. El corte se mueve al final de la
         * validación para que la construcción tampoco vea ese futuro.
         */
        ResultadoEvaluacion enPrueba = evaluador.evaluar(
                elegida, corte.plus(validacion), entrenamiento, prueba);

        log.info("Búsqueda: gana {} en validación (índice {}); en prueba NDCG@10 {}",
                elegida.nombre(), String.format("%.4f", mejor.indiceGlobal()),
                String.format("%.4f", enPrueba.ndcg10()));

        return new Resultado(elegida, enValidacion, enPrueba);
    }

    /**
     * Las cuatro hipótesis, cada una con un argumento detrás.
     *
     * <p>No son un barrido: son cuatro preguntas distintas sobre qué debería
     * pesar más, y por eso el resultado se puede interpretar.
     */
    public List<ConfiguracionRanker> candidatas() {
        ConfiguracionRanker base = ConfiguracionRanker.enProduccion(pesos);

        List<ConfiguracionRanker> lista = new ArrayList<>();
        // A · la que está sirviendo. Sin ella, «mejor» no significa nada.
        lista.add(new ConfiguracionRanker("v4-A-base", base.pesoPorOrigen(),
                base.penalizacionPopularidad()));
        // B · ¿vale más la conducta ajena que el gusto propio?
        lista.add(base.con("v4-B-colaborativo", Origen.COHORTE, 1.2));
        // C · ¿conviene explorar más de lo que se explora?
        lista.add(base.con("v4-C-explorador", Origen.EXPLORACION, 0.6));
        // D · ¿y castigar más lo sobreexpuesto?
        lista.add(new ConfiguracionRanker("v4-D-antipopular", base.pesoPorOrigen(), 0.60));

        return lista;
    }
}
