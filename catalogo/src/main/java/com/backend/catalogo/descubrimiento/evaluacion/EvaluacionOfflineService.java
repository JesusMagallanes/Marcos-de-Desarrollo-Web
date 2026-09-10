package com.backend.catalogo.descubrimiento.evaluacion;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.catalogo.descubrimiento.Origen;
import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;
import com.backend.catalogo.descubrimiento.evaluacion.ResultadoEvaluacion.Historial;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Responde la única pregunta de la fase 3: ¿esto mejora o no?
 *
 * <p>La idea es simple de contar y fácil de hacer mal. Se elige una fecha de
 * corte. Se construye el recomendador con lo que se sabía HASTA ese momento. Se
 * le pide su lista. Y se compara contra lo que la gente hizo DESPUÉS, que el
 * sistema no podía saber.
 *
 * <h4>La trampa que hay que evitar</h4>
 *
 * <p>La forma cómoda de escribir esto sería reutilizar las consultas de
 * producción. Y sería inválido: `perfil_faceta`, `item_relacion` y
 * `tendencia_item` son tablas de estado actual, se actualizan en sitio y no
 * guardan historia. Contienen ya el futuro respecto a cualquier corte del
 * pasado. Un evaluador que las usara construiría la recomendación con
 * información que en ese momento no existía y luego se felicitaría por acertar.
 *
 * <p>Por eso todo sale de {@code evento_interaccion}, la única tabla con fecha
 * por fila. Ver {@link EvaluacionRepository}.
 *
 * <h4>Fuera de la petición</h4>
 *
 * <p>Esto no se ejecuta nunca dentro de un {@code GET /home} ni hay endpoint que
 * lo dispare. Se llama desde una prueba o desde una tarea, sobre ventanas
 * acotadas. El Home sigue leyendo tablas ya calculadas.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EvaluacionOfflineService {

    private final EvaluacionRepository datos;
    private final PesosDescubrimiento pesos;

    /** Cuántas recomendaciones se generan por sujeto antes de recortar a K. */
    private static final int TOPE_LISTA = 10;

    /** A partir de cuántos eventos se considera que hay historia suficiente. */
    private static final int HISTORIAL_SUFICIENTE = 8;

    /**
     * Evalúa una configuración sobre un corte.
     *
     * @param corte la frontera; nada posterior entra en la construcción
     * @param entrenamiento cuánto pasado se usa para construir
     * @param holdout cuánto futuro se usa para comprobar
     */
    @Transactional(readOnly = true)
    public ResultadoEvaluacion evaluar(ConfiguracionRanker config, Instant corte,
            Duration entrenamiento, Duration holdout) {

        Instant desde = corte.minus(entrenamiento);
        Instant fin = corte.plus(holdout);

        // ── Señales, todas anteriores al corte ──
        Map<UUID, Map<Long, Double>> personal = porSujeto(
                datos.candidatosPersonales(desde, corte));
        Map<UUID, Map<Long, Double>> colaborativo = porSujeto(
                datos.candidatosColaborativos(desde, corte,
                        pesos.getColaborativoMinSoporte()));
        Map<Long, Double> popularidad = new HashMap<>();
        for (Object[] f : datos.popularidadEnCorte(desde, corte)) {
            popularidad.put(numero(f[0]).longValue(), numero(f[1]).doubleValue());
        }

        // ── La verdad, toda posterior ──
        Map<UUID, Set<Long>> verdad = new HashMap<>();
        for (Object[] f : datos.holdout(corte, fin)) {
            verdad.computeIfAbsent((UUID) f[0], s -> new HashSet<>())
                    .add(numero(f[1]).longValue());
        }

        Map<UUID, Set<Long>> yaTocado = new HashMap<>();
        for (Object[] f : datos.yaTocadoEnCorte(desde, corte)) {
            yaTocado.computeIfAbsent((UUID) f[0], s -> new HashSet<>())
                    .add(numero(f[1]).longValue());
        }

        Map<UUID, Integer> historial = new HashMap<>();
        for (Object[] f : datos.historialEnCorte(desde, corte)) {
            historial.put((UUID) f[0], numero(f[1]).intValue());
        }

        Map<Long, long[]> ficha = new HashMap<>();
        for (Object[] f : datos.fichaDeCatalogo()) {
            ficha.put(numero(f[0]).longValue(), new long[] {
                    f[1] == null ? -1 : numero(f[1]).longValue(),
                    f[2] == null ? -1 : numero(f[2]).longValue() });
        }

        return medir(config, personal, colaborativo, popularidad, verdad, historial,
                yaTocado, ficha, datos.catalogoElegible());
    }

    /**
     * Compara varias configuraciones sobre EL MISMO corte.
     *
     * <p>El mismo corte no es un detalle: dos configuraciones evaluadas sobre
     * ventanas distintas no se pueden comparar, porque la diferencia podría ser
     * de los datos y no de los pesos. Es el error que convierte una comparación
     * en una anécdota.
     */
    @Transactional(readOnly = true)
    public List<ResultadoEvaluacion> comparar(List<ConfiguracionRanker> configuraciones,
            Instant corte, Duration entrenamiento, Duration holdout) {

        List<ResultadoEvaluacion> resultados = new ArrayList<>();
        for (ConfiguracionRanker config : configuraciones) {
            resultados.add(evaluar(config, corte, entrenamiento, holdout));
        }
        return resultados;
    }

    /* ══════════════ El cálculo ══════════════ */

    private ResultadoEvaluacion medir(ConfiguracionRanker config,
            Map<UUID, Map<Long, Double>> personal,
            Map<UUID, Map<Long, Double>> colaborativo,
            Map<Long, Double> popularidad,
            Map<UUID, Set<Long>> verdad,
            Map<UUID, Integer> historial,
            Map<UUID, Set<Long>> yaTocado,
            Map<Long, long[]> ficha,
            long catalogoElegible) {

        Set<UUID> sujetos = new HashSet<>(verdad.keySet());
        // Solo cuentan los que estaban ahí antes del corte: a quien apareció
        // después no se le podía recomendar nada, y meterlo hundiría el recall
        // por un motivo que no tiene que ver con la calidad.
        sujetos.retainAll(historial.keySet());

        double sumaRecall5 = 0;
        double sumaRecall10 = 0;
        double sumaNdcg5 = 0;
        double sumaNdcg10 = 0;
        double sumaDivCategoria = 0;
        double sumaDivMarca = 0;
        double sumaNovedad = 0;
        double sumaRepeticion = 0;
        int conColaborativo = 0;
        int evaluados = 0;

        Set<Long> recomendadosAlguna = new HashSet<>();
        Map<Historial, double[]> porHistorial = new EnumMap<>(Historial.class);
        for (Historial h : Historial.values()) {
            porHistorial.put(h, new double[2]);
        }

        double popularidadMaxima = popularidad.values().stream()
                .mapToDouble(Double::doubleValue).max().orElse(1.0);

        for (UUID sujeto : sujetos) {
            Map<Long, Double> depersonal = personal.getOrDefault(sujeto, Map.of());
            Map<Long, Double> decolab = colaborativo.getOrDefault(sujeto, Map.of());

            List<Long> lista = ordenar(config, depersonal, decolab, popularidad,
                    yaTocado.getOrDefault(sujeto, Set.of()));
            if (lista.isEmpty()) {
                continue;
            }
            evaluados++;
            recomendadosAlguna.addAll(lista);
            if (!decolab.isEmpty()) {
                conColaborativo++;
            }

            Set<Long> acertar = verdad.getOrDefault(sujeto, Set.of());
            double r5 = recall(lista, acertar, 5);
            double r10 = recall(lista, acertar, 10);
            sumaRecall5 += r5;
            sumaRecall10 += r10;
            sumaNdcg5 += ndcg(lista, acertar, 5);
            sumaNdcg10 += ndcg(lista, acertar, 10);

            sumaDivCategoria += diversidad(lista, ficha, 0);
            sumaDivMarca += diversidad(lista, ficha, 1);
            sumaNovedad += novedad(lista, popularidad, popularidadMaxima);
            sumaRepeticion += repeticion(lista,
                    yaTocado.getOrDefault(sujeto, Set.of()));

            double[] acumulado = porHistorial.get(clasificar(historial.get(sujeto)));
            acumulado[0] += r10;
            acumulado[1] += 1;
        }

        if (evaluados == 0) {
            log.info("Evaluación {}: ningún sujeto evaluable en la ventana", config.nombre());
            return new ResultadoEvaluacion(config.nombre(), 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, Map.of());
        }

        Map<Historial, Double> recallPorHistorial = new EnumMap<>(Historial.class);
        porHistorial.forEach((h, a) ->
                recallPorHistorial.put(h, a[1] == 0 ? 0.0 : a[0] / a[1]));

        return new ResultadoEvaluacion(config.nombre(), evaluados,
                sumaRecall5 / evaluados, sumaRecall10 / evaluados,
                sumaNdcg5 / evaluados, sumaNdcg10 / evaluados,
                catalogoElegible == 0 ? 0.0 : (double) recomendadosAlguna.size() / catalogoElegible,
                sumaDivCategoria / evaluados, sumaDivMarca / evaluados,
                sumaNovedad / evaluados, sumaRepeticion / evaluados,
                (double) conColaborativo / evaluados,
                recallPorHistorial);
    }

    /**
     * El ranking, con la misma forma que el de producción.
     *
     * <p>Normaliza cada fuente por su máximo antes de aplicar el peso —si no,
     * gana la de unidades más grandes y no la mejor— y castiga la
     * sobreexposición al final. Es deliberadamente el mismo procedimiento que
     * {@code RankerHibrido}: si aquí se ordenara de otra manera, la evaluación
     * mediría un sistema que no existe.
     */
    private List<Long> ordenar(ConfiguracionRanker config,
            Map<Long, Double> personal, Map<Long, Double> colaborativo,
            Map<Long, Double> popularidad, Set<Long> yaTocado) {

        Map<Long, Double> total = new HashMap<>();
        acumular(total, personal, config.pesoDe(Origen.PERSONAL));
        acumular(total, colaborativo, config.pesoDe(Origen.COHORTE));
        acumular(total, popularidad, config.pesoDe(Origen.TENDENCIA));

        /*
         * Fuera lo que ya toco, venga de la senal que venga.
         *
         * Los dos generadores personalizados ya lo excluyen por su cuenta, pero
         * la popularidad no: es una lista global y no sabe nada de este sujeto.
         * Sin este filtro, «lo mas visto» le devolvia lo que acababa de mirar, y
         * la metrica de repeticion lo delato. Aqui, en un solo sitio, porque
         * cualquier senal que se anada manana tiene el mismo problema.
         */
        yaTocado.forEach(total::remove);

        return total.entrySet().stream()
                .map(e -> Map.entry(e.getKey(),
                        e.getValue() * config.factorPopularidad(
                                popularidad.getOrDefault(e.getKey(), 0.0))))
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(TOPE_LISTA)
                .map(Map.Entry::getKey)
                .toList();
    }

    private void acumular(Map<Long, Double> destino, Map<Long, Double> fuente, double peso) {
        if (fuente.isEmpty() || peso == 0) {
            return;
        }
        double maximo = fuente.values().stream()
                .mapToDouble(Double::doubleValue).max().orElse(0.0);
        if (maximo <= 0) {
            return;
        }
        fuente.forEach((item, score) ->
                destino.merge(item, score / maximo * peso, Double::sum));
    }

    /* ══════════════ Las métricas ══════════════ */

    /** Cuánto de lo que el sujeto hizo después estaba en el top K. */
    private double recall(List<Long> lista, Set<Long> acertar, int k) {
        if (acertar.isEmpty()) {
            return 0.0;
        }
        long aciertos = lista.stream().limit(k).filter(acertar::contains).count();
        return (double) aciertos / acertar.size();
    }

    /**
     * Como el recall, pero premiando acertar ARRIBA.
     *
     * <p>Importa porque casi nadie llega a la décima tarjeta: un acierto ahí
     * vale mucho menos que el mismo acierto en la primera, y una métrica que los
     * cuente igual esconde la diferencia entre ordenar bien y ordenar al azar.
     */
    private double ndcg(List<Long> lista, Set<Long> acertar, int k) {
        if (acertar.isEmpty()) {
            return 0.0;
        }
        double ganancia = 0;
        for (int i = 0; i < Math.min(k, lista.size()); i++) {
            if (acertar.contains(lista.get(i))) {
                ganancia += 1.0 / (Math.log(i + 2) / Math.log(2));
            }
        }
        // El ideal: todos los aciertos posibles, en las primeras posiciones.
        double ideal = 0;
        for (int i = 0; i < Math.min(k, acertar.size()); i++) {
            ideal += 1.0 / (Math.log(i + 2) / Math.log(2));
        }
        return ideal == 0 ? 0.0 : ganancia / ideal;
    }

    /** Proporción de valores distintos en la dimensión dada. 1 = ninguno repetido. */
    private double diversidad(List<Long> lista, Map<Long, long[]> ficha, int dimension) {
        Set<Long> distintos = new HashSet<>();
        for (Long item : lista) {
            long[] f = ficha.get(item);
            if (f != null && f[dimension] >= 0) {
                distintos.add(f[dimension]);
            }
        }
        return lista.isEmpty() ? 0.0 : (double) distintos.size() / lista.size();
    }

    /**
     * Cuánto se aleja la lista de los superventas.
     *
     * <p>Una lista llena de lo más popular puede acertar mucho y no descubrir
     * nada: esas cosas el usuario las habría encontrado solo. 1 = nada popular.
     */
    private double novedad(List<Long> lista, Map<Long, Double> popularidad, double maximo) {
        if (lista.isEmpty() || maximo <= 0) {
            return 0.0;
        }
        double suma = 0;
        for (Long item : lista) {
            suma += 1.0 - popularidad.getOrDefault(item, 0.0) / maximo;
        }
        return suma / lista.size();
    }

    /**
     * Porción de la lista que el sujeto ya había tocado antes del corte.
     *
     * <p>Debería salir 0: los dos generadores excluyen lo ya visto. Se mide de
     * todas formas, y esa es la gracia — es una comprobación viva de que la
     * exclusión sigue funcionando. Devolver 0 sin calcularlo no demostraría
     * nada, y el día que alguien rompiera la exclusión nadie se enteraría.
     */
    private double repeticion(List<Long> lista, Set<Long> yaTocado) {
        if (lista.isEmpty()) {
            return 0.0;
        }
        long repetidos = lista.stream().filter(yaTocado::contains).count();
        return (double) repetidos / lista.size();
    }

    private Historial clasificar(Integer eventos) {
        if (eventos == null || eventos == 0) {
            return Historial.SIN_HISTORIAL;
        }
        return eventos < HISTORIAL_SUFICIENTE ? Historial.ESCASO : Historial.SUFICIENTE;
    }

    /* ══════════════ Utilidades ══════════════ */

    private Map<UUID, Map<Long, Double>> porSujeto(List<Object[]> filas) {
        Map<UUID, Map<Long, Double>> mapa = new HashMap<>();
        for (Object[] f : filas) {
            mapa.computeIfAbsent((UUID) f[0], s -> new HashMap<>())
                    .merge(numero(f[1]).longValue(), numero(f[2]).doubleValue(), Double::sum);
        }
        return mapa;
    }

    private Number numero(Object valor) {
        return (Number) valor;
    }
}
