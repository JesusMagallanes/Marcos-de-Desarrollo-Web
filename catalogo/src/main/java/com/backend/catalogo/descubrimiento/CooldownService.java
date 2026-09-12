package com.backend.catalogo.descubrimiento;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

/**
 * Dejar de insistir poco a poco, en vez de dejar de insistir de golpe.
 *
 * <h4>Lo que había</h4>
 *
 * <p>Una regla de tres estados que en realidad eran dos: con cero, una o dos
 * impresiones sin clic no pasaba nada; con la tercera, el producto desaparecía
 * de todos los carruseles durante catorce días. Un escalón, y colocado en un
 * sitio donde los dos lados están mal. Por abajo, porque enseñar algo dos veces
 * sin que nadie lo toque no puede costar lo mismo que no haberlo enseñado. Por
 * arriba, porque convertía una exposición pasada en una sentencia: el producto
 * no bajaba de puesto, se iba, y no tenía forma de volver a demostrar nada
 * hasta que la ventana corriera entera.
 *
 * <p>Y era ciega al módulo. Tres apariciones en «relacionados» borraban el
 * producto de «lo más popular», donde quizá no había salido nunca. Eso no es
 * dejar de insistir, es castigar en un sitio por lo que pasó en otro.
 *
 * <h4>Lo que hay</h4>
 *
 * <p>Un enfriamiento graduado que se calcula por {@code (sujeto, ítem, módulo)}
 * con la misma aritmética que ya usa el proyecto para frenar lo popular:
 * {@code 1 / (1 + k·ln(1+n))}. Cada impresión sin respuesta resta un poco, las
 * primeras mucho más que las siguientes, y solo cuando la evidencia se acumula
 * de verdad —el doble del antiguo tope— el producto sale de ESE módulo.
 *
 * <h4>Tres cosas que este servicio NO hace</h4>
 *
 * <ol>
 *   <li><b>No resucita nada.</b> El enfriamiento es una penalización y, en su
 *       extremo, una exclusión. Nunca sube a nadie. Un producto marcado como
 *       «no me interesa» sigue fuera de todos los módulos por la vía de
 *       siempre, y este cálculo no lo toca ni podría: son listas distintas que
 *       se suman, no se restan.</li>
 *   <li><b>No guarda nada.</b> Ni tabla nueva ni columna nueva. Todo sale de
 *       {@code impresion}, que ya tiene sujeto, ítem, módulo, fecha y clic.</li>
 *   <li><b>No necesita que nadie lo despierte.</b> La recuperación no es una
 *       tarea programada: la ventana es deslizante, así que una impresión vieja
 *       deja de contarse sola.</li>
 * </ol>
 */
@Service
public class CooldownService {

    /** Cuánto cuesta calcular el enfriamiento de una pantalla entera. */
    public static final String TIEMPO = "smartzone_descubrimiento_cooldown_segundos";

    /** Candidatos retirados de un módulo por enfriamiento, agregados. */
    public static final String ENFRIADOS = "smartzone_descubrimiento_enfriados_total";

    private final ImpresionRepository impresiones;
    private final PesosDescubrimiento pesos;
    private final MeterRegistry registro;
    private final Timer cronometro;

    public CooldownService(ImpresionRepository impresiones, PesosDescubrimiento pesos,
            MeterRegistry registro) {
        this.impresiones = impresiones;
        this.pesos = pesos;
        this.registro = registro;
        this.cronometro = Timer.builder(TIEMPO)
                .description("Tiempo de calcular el enfriamiento de un sujeto")
                .register(registro);
    }

    /**
     * El enfriamiento de un sujeto, ya resuelto para todos los módulos.
     *
     * <p>Es un valor inmutable y calculado una sola vez por petición: los seis
     * carruseles del Home consultan este mismo objeto. Que sea inmutable es lo
     * que hace que la respuesta sea idéntica en los seis, y lo que permite
     * afirmar —y probar— que la misma entrada produce el mismo resultado.
     *
     * @param factores por módulo, cuánto conserva cada ítem penalizado. Lo que
     *     no aparece vale 1: sin impresiones no hay castigo
     * @param bloqueados por módulo, lo que ha pasado del corte y no se ofrece
     *     ahí. Son filtro duro, no una nota para el ranking
     */
    public record Cooldown(
            Map<ModuloDescubrimiento, Map<Long, Double>> factores,
            Map<ModuloDescubrimiento, List<Long>> bloqueados) {

        /** Sin sujeto no hay historial, y sin historial no hay nada que enfriar. */
        public static Cooldown ninguno() {
            return new Cooldown(Map.of(), Map.of());
        }

        /** Cuánto conserva este candidato en este carrusel. 1 es «intacto». */
        public double factor(ModuloDescubrimiento modulo, Long itemId) {
            return factores.getOrDefault(modulo, Map.of()).getOrDefault(itemId, 1.0);
        }

        /** Lo que no puede ofrecerse en este carrusel. Va a la lista de exclusiones. */
        public List<Long> bloqueadosEn(ModuloDescubrimiento modulo) {
            return bloqueados.getOrDefault(modulo, List.of());
        }
    }

    /**
     * Calcula el enfriamiento del sujeto con UNA consulta.
     *
     * <p>Una por petición, no una por módulo y desde luego no una por
     * candidato: el N+1 que estas fases tienen prohibido. El {@link Timer} está
     * para que «esto no cuesta nada» sea una medición y no una opinión.
     */
    @Transactional(readOnly = true)
    public Cooldown de(UUID sujeto) {
        if (sujeto == null) {
            return Cooldown.ninguno();
        }
        return cronometro.record(() -> calcular(sujeto));
    }

    private Cooldown calcular(UUID sujeto) {
        Instant ahora = Instant.now();
        Instant desde = ahora.minus(pesos.getDiasSupresionPorFatiga(), ChronoUnit.DAYS);

        Map<Long, Map<String, Integer>> vecesPorItem = new HashMap<>();
        Map<Long, Integer> totalPorItem = new HashMap<>();
        Set<Long> tocados = new HashSet<>();

        for (Object[] fila : impresiones.exposicionPorModulo(
                sujeto, TipoItem.PRODUCTO.name(), desde, ahora)) {

            Long itemId = ((Number) fila[0]).longValue();
            String modulo = String.valueOf(fila[1]);
            int veces = ((Number) fila[2]).intValue();

            if (((Number) fila[3]).longValue() > 0) {
                tocados.add(itemId);
            }
            vecesPorItem.computeIfAbsent(itemId, k -> new HashMap<>()).merge(modulo, veces,
                    Integer::sum);
            totalPorItem.merge(itemId, veces, Integer::sum);
        }
        return repartirPorModulo(vecesPorItem, totalPorItem, tocados);
    }

    /**
     * Convierte los conteos en un factor por módulo, o en una exclusión.
     *
     * <p>Todo en memoria sobre las filas que ya trajo la consulta. Recorrer los
     * módulos aquí en vez de preguntar por cada uno es lo que convierte seis
     * consultas en una.
     */
    private Cooldown repartirPorModulo(Map<Long, Map<String, Integer>> vecesPorItem,
            Map<Long, Integer> totalPorItem, Set<Long> tocados) {

        Map<ModuloDescubrimiento, Map<Long, Double>> factores =
                new EnumMap<>(ModuloDescubrimiento.class);
        Map<ModuloDescubrimiento, List<Long>> bloqueados =
                new EnumMap<>(ModuloDescubrimiento.class);

        for (ModuloDescubrimiento modulo : ModuloDescubrimiento.values()) {
            Map<Long, Double> delModulo = new HashMap<>();
            List<Long> fuera = new ArrayList<>();

            for (Map.Entry<Long, Map<String, Integer>> entrada : vecesPorItem.entrySet()) {
                Long itemId = entrada.getKey();

                /*
                 * ACCION POSITIVA: el item queda perdonado, y en todos los
                 * modulos.
                 *
                 * No es una semantica nueva. `con_clic` ya existe y ya lo pone
                 * la ingesta cuando llega cualquier evento que no sea un
                 * descarte y venga de una superficie de recomendacion — el
                 * mismo criterio que usaba la regla anterior, que exigia CERO
                 * clics para bloquear. Se conserva tal cual: el enfriamiento
                 * existe para dejar de insistir con lo que nadie quiere, y algo
                 * que la persona abrio no entra en esa descripcion.
                 */
                if (tocados.contains(itemId)) {
                    continue;
                }
                double efectivas = efectivas(modulo, entrada.getValue(),
                        totalPorItem.getOrDefault(itemId, 0));

                if (efectivas >= pesos.getCooldownMaximo()) {
                    fuera.add(itemId);
                } else if (efectivas > 0) {
                    delModulo.put(itemId, pesos.factorCooldown(efectivas));
                }
            }
            if (!delModulo.isEmpty()) {
                factores.put(modulo, delModulo);
            }
            if (!fuera.isEmpty()) {
                bloqueados.put(modulo, fuera);
                anotar(modulo, fuera.size());
            }
        }
        return new Cooldown(factores, bloqueados);
    }

    /**
     * Las impresiones que pesan sobre este módulo.
     *
     * <p>Las suyas enteras y las de los demás a peso reducido. La alternativa
     * de contarlas todas igual es la regla anterior con más pasos; la de
     * ignorarlas es dejar que un mismo producto persiga a alguien por toda la
     * pantalla sin que ningún contador se entere.
     */
    private double efectivas(ModuloDescubrimiento modulo, Map<String, Integer> porModulo,
            int total) {
        int propias = porModulo.getOrDefault(modulo.name(), 0);
        int ajenas = total - propias;
        return propias + pesos.getCooldownCruzado() * ajenas;
    }

    /**
     * Cuántos se retiraron, sin decir cuáles.
     *
     * <p>La etiqueta es el módulo, que es un enum de seis valores. Ni sujeto,
     * ni producto, ni sesión: una métrica etiquetada por producto delata
     * conducta y, de paso, revienta la cardinalidad de Prometheus.
     */
    private void anotar(ModuloDescubrimiento modulo, int cuantos) {
        registro.counter(ENFRIADOS, Tags.of("modulo", modulo.name())).increment(cuantos);
    }
}
