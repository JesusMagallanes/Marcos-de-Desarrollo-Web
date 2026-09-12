package com.backend.catalogo.descubrimiento;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * Quién puede llegar a ser candidato, comprobado en el momento de servir.
 *
 * <h4>El agujero que tapa</h4>
 *
 * <p>Los siete generadores filtran por estado de moderación y por stock dentro
 * de su propio SQL, así que lo que devuelven es elegible en ese instante. Hay
 * una excepción y es la que importa: {@code tendencia_item} es una tabla
 * derivada que guarda IDENTIFICADORES y se recalcula cada hora. Los ids salían
 * de ahí y se cargaban con {@code porIds}, que no comprueba nada.
 *
 * <p>El resultado era una recomendación rancia con cara visible: un producto
 * que se agota —o que un moderador rechaza— a las 10:05 seguía apareciendo en
 * «Lo más visto en tu zona» hasta las 11:00. Alguien entra desde el carrusel y
 * se encuentra una ficha sin stock, que es la peor manera de gastar la
 * confianza de un visitante.
 *
 * <h4>Antes del ranker, no después</h4>
 *
 * <p>Esta comprobación se aplica en cuanto los identificadores dejan de ser
 * datos derivados y ANTES de que entren en la lista de candidatos. El orden
 * importa: filtrar después de ordenar dejaría huecos en el carrusel —se
 * descartarían elementos ya elegidos— y, peor, permitiría que algún día un
 * cambio de ranking hiciera reaparecer algo que no debía existir.
 *
 * <h4>Una consulta, no una por candidato</h4>
 *
 * <p>Un solo {@code IN} sobre la decena o dos de identificadores que vienen del
 * lote. Preguntar producto a producto sería el N+1 que esta fase tiene
 * prohibido introducir, y el {@link Timer} está precisamente para que la
 * afirmación «esto no cuesta nada» deje de ser una opinión.
 */
@Service
@Slf4j
public class ElegibilidadService {

    /** Cuánto cuesta revalidar. Se mira en {@code /actuator/prometheus}. */
    public static final String TIEMPO = "smartzone_descubrimiento_elegibilidad_segundos";

    /** Identificadores descartados por dejar de ser publicables. */
    public static final String DESCARTADOS = "smartzone_descubrimiento_no_elegibles_total";

    private final ProductoElegibleRepository repositorio;
    private final MeterRegistry registro;
    private final Timer cronometro;

    public ElegibilidadService(ProductoElegibleRepository repositorio, MeterRegistry registro) {
        this.repositorio = repositorio;
        this.registro = registro;
        this.cronometro = Timer.builder(TIEMPO)
                .description("Tiempo de revalidar la elegibilidad de un lote de candidatos")
                .register(registro);
    }

    /**
     * Deja solo los que hoy pueden mostrarse, conservando el orden recibido.
     *
     * <p>El orden se conserva porque quien llama ya decidió una prelación —la
     * tendencia viene ordenada por su propio score— y reordenar aquí
     * convertiría una comprobación en una decisión de ranking, que no es su
     * trabajo.
     *
     * @param ids candidatos en el orden en que se pensaban servir
     * @return los mismos, en el mismo orden, sin los que dejaron de ser
     *     publicables
     */
    @Transactional(readOnly = true)
    public List<Long> filtrar(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return cronometro.record(() -> {
            Set<Long> vivos = new HashSet<>(repositorio.elegiblesDe(ids));
            List<Long> resultado = ids.stream().filter(vivos::contains).toList();

            int caidos = ids.size() - resultado.size();
            if (caidos > 0) {
                /*
                 * Sin identificadores en el log: solo cuántos. Saber que el
                 * lote horario va rancio es útil; saber qué producto concreto
                 * se cayó no lo es, y un log es lo más fácil de filtrar sin
                 * querer.
                 */
                registro.counter(DESCARTADOS).increment(caidos);
                log.debug("Elegibilidad: {} de {} candidatos dejaron de ser publicables",
                        caidos, ids.size());
            }
            return resultado;
        });
    }
}
