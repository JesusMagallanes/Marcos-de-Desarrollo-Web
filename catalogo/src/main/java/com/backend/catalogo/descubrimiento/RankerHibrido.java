package com.backend.catalogo.descubrimiento;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;

import lombok.RequiredArgsConstructor;

/**
 * Pone en la misma escala candidatos que vienen de generadores distintos.
 *
 * <p>El problema que resuelve es concreto y no es obvio: cada generador puntúa
 * en unidades suyas. El de intereses devuelve la suma de scores del perfil, que
 * puede valer 40; el colaborativo devuelve una suma de cosenos, que rara vez
 * pasa de 3; el de tendencia devuelve velocidad por crecimiento. Sumarlos tal
 * cual no es combinar señales: es dejar que gane el generador con las unidades
 * más grandes, que además es el que menos tiene que ver con la calidad.
 *
 * <p>Por eso se normaliza cada grupo por su propio máximo ANTES de aplicar los
 * pesos. A partir de ahí los pesos de {@link PesosDescubrimiento} significan lo
 * que parece que significan —cuánto se cree a cada fuente— y se pueden mover sin
 * saber nada de las unidades internas de nadie.
 *
 * <p>No sustituye al ranking existente. Cada carrusel sigue ordenándose por su
 * propio criterio; esto se usa cuando hay que MEZCLAR, que es lo que la fase 1
 * no necesitaba hacer nunca.
 */
@Component
@RequiredArgsConstructor
public class RankerHibrido {

    private final ImpresionRepository impresiones;
    private final PesosDescubrimiento pesos;

    /** Ventana de exposición que se mira para castigar la popularidad. */
    private static final Duration VENTANA_EXPOSICION = Duration.ofDays(7);

    /**
     * Combina, castiga la sobreexposición y ordena.
     *
     * <p>El orden de las tres operaciones no es intercambiable. Normalizar
     * primero, porque si no los pesos no significan nada. Castigar después,
     * sobre el score ya comparable. Ordenar al final.
     *
     * <p>Un producto que aparece por varios caminos se queda con el mejor de
     * sus scores y con la razón que lo consiguió, en vez de sumarlos: sumar
     * premiaría estar en todas partes, que es exactamente el sesgo que este
     * ranker existe para frenar.
     */
    public List<CandidatoConRazon> combinar(List<CandidatoConRazon> candidatos) {
        if (candidatos.isEmpty()) {
            return List.of();
        }

        Map<Origen, Double> maximo = new HashMap<>();
        for (CandidatoConRazon c : candidatos) {
            maximo.merge(c.origen(), Math.abs(c.score()), Math::max);
        }

        Map<Long, CandidatoConRazon> mejorPorItem = new LinkedHashMap<>();
        for (CandidatoConRazon c : candidatos) {
            double tope = maximo.getOrDefault(c.origen(), 0.0);
            // Un grupo entero a cero no se divide: se queda a cero.
            double normalizado = tope > 0 ? c.score() / tope : 0.0;
            double ponderado = normalizado * pesos.pesoDe(c.origen());

            CandidatoConRazon actual = mejorPorItem.get(c.itemId());
            if (actual == null || ponderado > actual.score()) {
                mejorPorItem.put(c.itemId(), c.conScore(ponderado));
            }
        }

        aplicarPenalizacionPorExposicion(mejorPorItem);

        return mejorPorItem.values().stream()
                .sorted(Comparator.comparingDouble(CandidatoConRazon::score).reversed())
                .toList();
    }

    /**
     * Castiga lo que ya se enseña demasiado.
     *
     * <p>Sin esto el sistema se muerde la cola: un producto se recomienda, al
     * recomendarse se ve, al verse acumula señal, y esa señal lo vuelve a
     * recomendar. Al cabo de unas semanas media tienda es invisible y no porque
     * sea peor, sino porque nunca le tocó salir.
     *
     * <p>Se mide la exposición, no las ventas: lo que hay que corregir es
     * cuántas veces lo ha enseñado el sistema, que es la parte de la que el
     * sistema es responsable.
     */
    private void aplicarPenalizacionPorExposicion(Map<Long, CandidatoConRazon> porItem) {
        if (porItem.isEmpty()) {
            return;
        }
        // Sobre los candidatos ya recortados: unas decenas de identificadores.
        List<Object[]> conteos = impresiones.contarPorItem(
                List.copyOf(porItem.keySet()), Instant.now().minus(VENTANA_EXPOSICION));

        for (Object[] fila : conteos) {
            Long itemId = ((Number) fila[0]).longValue();
            long veces = ((Number) fila[1]).longValue();

            CandidatoConRazon c = porItem.get(itemId);
            if (c != null) {
                porItem.put(itemId, c.conScore(c.score() * pesos.factorPopularidad(veces)));
            }
        }
    }
}
