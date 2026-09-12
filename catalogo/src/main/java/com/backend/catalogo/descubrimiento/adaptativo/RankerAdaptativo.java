package com.backend.catalogo.descubrimiento.adaptativo;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.backend.catalogo.descubrimiento.CandidatoConRazon;
import com.backend.catalogo.descubrimiento.ImpresionRepository;
import com.backend.catalogo.descubrimiento.MetricasPipeline;
import com.backend.catalogo.descubrimiento.Origen;
import com.backend.catalogo.descubrimiento.RankerHibrido;
import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;
import com.backend.catalogo.descubrimiento.adaptativo.AsignacionExperimento.Variante;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * El ranker de la fase 4: la misma aritmética, pero según el contexto.
 *
 * <p>No sustituye al de la fase 3, lo envuelve. Y eso no es cortesía
 * arquitectónica: es el plan de vuelta atrás. Si la calibración adaptativa está
 * apagada, o si algo falla dentro, la lista la produce {@link RankerHibrido}
 * exactamente igual que antes. El sistema no puede quedarse sin recomendar
 * porque una mejora no supo aplicarse.
 *
 * <h4>Qué añade</h4>
 *
 * <ol>
 *   <li><b>Pesos por contexto.</b> Home y ficha no son la misma pregunta, y a
 *       quien no se conoce no se le puede personalizar.</li>
 *   <li><b>Exploración con presupuesto.</b> Una parte de los huecos se reserva a
 *       lo que el sistema NO tiene claro, y esa parte crece cuanto menos sabe.</li>
 *   <li><b>Variantes.</b> Dos calibraciones sirviendo a la vez, para poder
 *       compararlas con datos y no con opiniones.</li>
 * </ol>
 *
 * <h4>Lo que NO puede tocar</h4>
 *
 * <p>Las exclusiones. Los candidatos llegan aquí ya filtrados —los descartes y
 * la fatiga se aplican en el SQL, antes— y este ranker solo REORDENA lo que
 * recibe. No añade candidatos de ninguna parte, ni siquiera al explorar: la
 * exploración elige entre los mismos, y por eso no puede resucitar nada.
 * Cualquier otra forma de explorar habría abierto una puerta trasera a los
 * negativos.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RankerAdaptativo {

    private final RankerHibrido hibrido;
    private final ImpresionRepository impresiones;
    private final MetricasPipeline cronometros;
    private final AsignacionExperimento asignacion;
    private final PesosAdaptativos pesos;
    /*
     * El piso de sujetos NO es un peso de la fase 4: es una proteccion del
     * calculo de exposicion, compartida con el ranker estable. Vive en la
     * configuracion comun para que los dos lean el mismo numero y no puedan
     * separarse con el tiempo.
     */
    private final PesosDescubrimiento comunes;
    private final MetricasAdaptativas metricas;

    /** Ventana de exposición que se mira para la novedad y el freno. */
    private static final Duration VENTANA_EXPOSICION = Duration.ofDays(7);

    /**
     * Ordena según el contexto, o cae al ranker estable si no puede.
     *
     * @param eventosDelPerfil cuánta evidencia sostiene el perfil; decide cuánto
     *     se explora
     */
    public List<CandidatoConRazon> ordenar(List<CandidatoConRazon> candidatos,
            ContextoRanking contexto, UUID sujeto, int eventosDelPerfil) {

        if (!pesos.isActivo() || candidatos.isEmpty()) {
            return hibrido.combinar(candidatos);
        }
        try {
            Variante variante = asignacion.de(sujeto);
            metricas.varianteServida(variante, contexto);

            /*
             * Las tres etapas, cronometradas por separado.
             *
             * Juntas no dicen nada util: `extraer` hace una consulta y las otras
             * dos son aritmetica en memoria, asi que un unico numero solo
             * responderia «el ranker tarda», que ya se sabia. Separadas dicen si
             * el coste esta en la base o en el calculo, que son dos problemas
             * con soluciones opuestas.
             */
            String superficie = contexto.superficie().name();

            List<CaracteristicasCandidato> rasgos = cronometros.comun(
                    superficie, MetricasPipeline.EXTRAER,
                    () -> extraer(candidatos, superficie));

            List<CandidatoConRazon> ordenados = cronometros.comun(
                    superficie, MetricasPipeline.PUNTUAR,
                    () -> puntuar(candidatos, rasgos, contexto, variante));

            return cronometros.comun(superficie, MetricasPipeline.EXPLORAR,
                    () -> explorar(ordenados, rasgos, contexto, eventosDelPerfil));

        } catch (RuntimeException fallo) {
            /*
             * El modo de fallo que exige esta fase: una calibracion mal escrita,
             * un contexto inesperado o cualquier otra sorpresa NO puede dejar al
             * Home sin recomendaciones. Se cae al ranker de la fase 3, que
             * lleva meses funcionando, y se deja constancia para arreglarlo.
             */
            log.warn("El ranker adaptativo falló ({}); se sirve con el estable",
                    fallo.getMessage());
            metricas.caidaAlEstable();
            return hibrido.combinar(candidatos);
        }
    }

    /* ══════════════ Etapa 1 · características ══════════════ */

    /**
     * Convierte los candidatos en vectores, con UNA consulta para todos.
     *
     * <p>La exposición se pide por lotes sobre los identificadores ya
     * recortados: unas decenas. Preguntarla por candidato sería exactamente el
     * N+1 que esta fase tiene prohibido introducir.
     */
    private List<CaracteristicasCandidato> extraer(List<CandidatoConRazon> candidatos,
            String superficie) {
        List<Long> ids = candidatos.stream().map(CandidatoConRazon::itemId).distinct().toList();

        /*
         * La consulta va con su propio cronometro dentro del de `extraer`.
         *
         * Es lo que permite separar «la exposicion cuesta» de «convertir sus
         * filas cuesta». Sin partirlo, una regresion en cualquiera de los dos
         * lados se veria igual y se buscaria en el sitio equivocado.
         */
        List<Object[]> filas = cronometros.comun(superficie, MetricasPipeline.EXPOSICION,
                () -> impresiones.contarPorItem(ids, Instant.now().minus(VENTANA_EXPOSICION),
                        comunes.getExposicionMinimaSujetos()));

        Map<Long, Long> exposicion = new HashMap<>();
        for (Object[] fila : filas) {
            exposicion.put(((Number) fila[0]).longValue(), ((Number) fila[1]).longValue());
        }
        long maximaExposicion = exposicion.values().stream().mapToLong(Long::longValue).max()
                .orElse(0L);

        // Cada familia se normaliza por su maximo, igual que en la fase 3: sin
        // eso los pesos no significan lo que parece que significan.
        Map<Origen, Double> tope = new HashMap<>();
        for (CandidatoConRazon c : candidatos) {
            tope.merge(c.origen(), Math.abs(c.score()), Math::max);
        }

        List<CaracteristicasCandidato> rasgos = new ArrayList<>(candidatos.size());
        for (int i = 0; i < candidatos.size(); i++) {
            CandidatoConRazon c = candidatos.get(i);
            double techo = tope.getOrDefault(c.origen(), 0.0);
            double normalizado = techo > 0 ? c.score() / techo : 0.0;
            long vistoVeces = exposicion.getOrDefault(c.itemId(), 0L);

            rasgos.add(new CaracteristicasCandidato(
                    c.itemId(), c.categoriaId(), c.marcaId(), c.origen(), c.razon(),
                    c.origen() == Origen.PERSONAL ? normalizado : 0.0,
                    c.origen() == Origen.COHORTE ? normalizado : 0.0,
                    (c.origen() == Origen.TENDENCIA || c.origen() == Origen.GEO)
                            ? normalizado : 0.0,
                    vistoVeces,
                    maximaExposicion == 0 ? 1.0 : 1.0 - (double) vistoVeces / maximaExposicion,
                    i));
        }
        return rasgos;
    }

    /* ══════════════ Etapa 2 · puntuación ══════════════ */

    /**
     * La fórmula, que es deliberadamente una suma ponderada.
     *
     * <p>Lineal y no algo más sofisticado porque una suma se puede leer, se
     * puede explicar y se puede reproducir a mano cuando algo sale raro. Un
     * modelo que acertara un poco más y no se pudiera depurar sería un mal
     * negocio en un sistema del que hay que responder.
     *
     * <p>Un producto que llega por varios caminos se queda con su mejor score y
     * con la razón que lo consiguió, igual que en la fase 3: sumar premiaría
     * estar en todas partes, que es el sesgo que se lleva frenando desde
     * entonces.
     */
    private List<CandidatoConRazon> puntuar(List<CandidatoConRazon> candidatos,
            List<CaracteristicasCandidato> rasgos, ContextoRanking contexto, Variante variante) {

        boolean esVariante = variante == Variante.VARIANTE;
        Map<Long, CandidatoConRazon> mejorPorItem = new LinkedHashMap<>();

        for (int i = 0; i < candidatos.size(); i++) {
            CaracteristicasCandidato r = rasgos.get(i);

            double senal = 0.0;
            for (Origen origen : Origen.values()) {
                senal += pesos.pesoDe(contexto, origen, esVariante) * r.scoreDe(origen);
            }
            // El freno por exposicion va fuera de la suma a proposito: es un
            // factor, no una senal mas. Sumandolo, subir un peso positivo podria
            // anularlo sin que nadie lo viera.
            double conFreno = senal * factorExposicion(r.exposicionReciente());

            CandidatoConRazon actual = mejorPorItem.get(r.itemId());
            if (actual == null || conFreno > actual.score()) {
                mejorPorItem.put(r.itemId(), candidatos.get(i).conScore(conFreno));
            }
        }

        return mejorPorItem.values().stream()
                .sorted(Comparator.comparingDouble(CandidatoConRazon::score).reversed())
                .toList();
    }

    private double factorExposicion(long veces) {
        if (veces <= 0) {
            return 1.0;
        }
        return 1.0 / (1.0 + pesos.getPenalizacionExposicion() * Math.log1p(veces));
    }

    /* ══════════════ Etapa 3 · exploración ══════════════ */

    /**
     * Reserva huecos para lo que el sistema no tiene claro.
     *
     * <p>Explotar sin explorar es cómodo y se paga después: el sistema se queda
     * encerrado en lo que ya sabe de alguien, deja de descubrirle nada y acaba
     * pareciendo repetitivo justo con quien más lo usa.
     *
     * <p>Los candidatos de exploración salen de la MISMA lista, elegidos por
     * novedad —los menos expuestos— entre los que no entraron por score. No son
     * aleatorios: siguen siendo candidatos legítimos que pasaron todos los
     * filtros. Eso es lo que hace que explorar no pueda resucitar un descarte ni
     * saltarse la fatiga.
     *
     * <p>Se insertan intercalados a partir de la segunda posición: la primera
     * tarjeta es la que más se mira y no es sitio para una apuesta.
     */
    private List<CandidatoConRazon> explorar(List<CandidatoConRazon> ordenados,
            List<CaracteristicasCandidato> rasgos, ContextoRanking contexto,
            int eventosDelPerfil) {

        double presupuesto = pesos.presupuestoExploracion(contexto.conPerfil(), eventosDelPerfil);
        int huecos = (int) Math.floor(ordenados.size() * presupuesto);
        if (huecos <= 0 || ordenados.size() < 4) {
            return ordenados;
        }

        Map<Long, Double> novedadPorItem = new HashMap<>();
        rasgos.forEach(r -> novedadPorItem.merge(r.itemId(), r.novedad(), Math::max));

        // La mitad de arriba se queda como esta; se explora sustituyendo en la
        // de abajo, que es donde el orden por score ya distingue poco.
        int frontera = Math.max(2, ordenados.size() / 2);
        List<CandidatoConRazon> cabeza = new ArrayList<>(ordenados.subList(0, frontera));
        List<CandidatoConRazon> cola = new ArrayList<>(
                ordenados.subList(frontera, ordenados.size()));

        cola.sort(Comparator.comparingDouble(
                (CandidatoConRazon c) -> novedadPorItem.getOrDefault(c.itemId(), 0.0)).reversed());

        metricas.exploracionAplicada(contexto, Math.min(huecos, cola.size()));

        List<CandidatoConRazon> salida = new ArrayList<>(cabeza);
        salida.addAll(cola);
        return salida;
    }
}
