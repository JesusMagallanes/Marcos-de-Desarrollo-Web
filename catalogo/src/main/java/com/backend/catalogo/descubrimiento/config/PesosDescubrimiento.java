package com.backend.catalogo.descubrimiento.config;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.backend.catalogo.descubrimiento.TipoEvento;

import lombok.Getter;
import lombok.Setter;

/**
 * Toda la calibración del recomendador, en un solo sitio.
 *
 * <p>Los pesos no son constantes del código a propósito. Son lo primero que
 * hay que ajustar cuando empiezan a llegar datos reales, y tener que
 * recompilar y desplegar para mover un número mata la capacidad de aprender.
 * Se sobrescriben desde {@code application.properties} con el prefijo
 * {@code descubrimiento.*} o por variable de entorno.
 *
 * <p>Los valores por defecto son un punto de partida razonado, no una
 * verdad: la proporción entre ellos importa mucho más que su magnitud, porque
 * el score se normaliza al leer el perfil.
 */
@Component
@ConfigurationProperties(prefix = "descubrimiento")
@Getter
@Setter
public class PesosDescubrimiento {

    /** Peso de cada tipo de evento. Las señales negativas van en negativo. */
    private Map<TipoEvento, Double> pesos = pesosPorDefecto();

    /**
     * Cada cuánto se reduce a la mitad el peso de lo ya ocurrido.
     *
     * <p>Es lo que hace que el perfil evolucione: un interés de hace tres
     * semanas conserva un octavo de su fuerza, así que sigue contando pero no
     * manda sobre lo de ayer.
     */
    private Duration vidaMedia = Duration.ofDays(7);

    /**
     * Techo del tiempo de permanencia, en segundos.
     *
     * <p>Una pestaña olvidada no es interés. Sin techo, veinte minutos de
     * descuido pesarían más que una compra.
     */
    private int dwellMaximoSegundos = 180;

    /** Referencia para normalizar la permanencia; ver {@code factorDwell}. */
    private int dwellReferenciaSegundos = 30;

    /**
     * Cuántos eventos hacen falta para creerse una faceta.
     *
     * <p>Con {@code confianza = 1 − e^(−n/k)}: un solo evento vale 0,12 y no
     * llena el Home de macetas porque alguien miró una maceta.
     */
    private double confianzaK = 8.0;

    /** Mínimo de sujetos distintos para servir un dato agregado. */
    private int minimoSujetos = 50;

    /** Impresiones sin clic tras las que se deja de mostrar un ítem. */
    private int topeImpresionesSinClic = 3;

    /** Días que dura ese castigo. */
    private int diasSupresionPorFatiga = 14;

    /** Cuánto del espacio se reserva a explorar en vez de acertar. */
    private double proporcionExploracion = 0.20;

    /** Cuánto se atenúa el interés al subir un nivel del árbol de categorías. */
    private double atenuacionPorNivel = 0.5;

    /** Máximo de ítems de la misma categoría dentro de un carrusel. */
    private int maximoPorCategoria = 3;

    /** Máximo de ítems de la misma marca dentro de un carrusel. */
    private int maximoPorMarca = 2;

    /**
     * El factor por permanencia, ya acotado.
     *
     * <p>Logarítmico y con techo: 10 s dan 1,28; 60 s dan 1,95; y media hora
     * da lo mismo que tres minutos, que es lo que se quiere.
     */
    public double factorDwell(Integer dwellMs) {
        if (dwellMs == null || dwellMs <= 0) {
            return 1.0;
        }
        double segundos = Math.min(dwellMs / 1000.0, dwellMaximoSegundos);
        return 1.0 + Math.log1p(segundos / dwellReferenciaSegundos);
    }

    /**
     * La saturación del n-ésimo evento igual en la misma sesión.
     *
     * <p>Refrescar una ficha veinte veces no son veinte intereses. El primero
     * vale 1,00; el tercero 0,48; el décimo 0,30.
     */
    public double factorSaturacion(int repeticion) {
        return 1.0 / (1.0 + Math.log(Math.max(1, repeticion)));
    }

    /** Cuánta evidencia sostiene una faceta, entre 0 y 1. */
    public double confianza(int eventos) {
        return 1.0 - Math.exp(-eventos / confianzaK);
    }

    public double pesoDe(TipoEvento tipo) {
        return pesos.getOrDefault(tipo, 0.0);
    }

    private static Map<TipoEvento, Double> pesosPorDefecto() {
        Map<TipoEvento, Double> p = new EnumMap<>(TipoEvento.class);
        p.put(TipoEvento.ITEM_VIEW, 1.0);
        p.put(TipoEvento.CATEGORY_VIEW, 0.5);
        p.put(TipoEvento.ITEM_VIEW_DEEP, 2.5);
        p.put(TipoEvento.SEARCH, 2.0);
        p.put(TipoEvento.SEARCH_CLICK, 3.0);
        p.put(TipoEvento.ATTRIBUTE_FILTER, 3.0);
        p.put(TipoEvento.COMPARE, 5.0);
        p.put(TipoEvento.SHARE, 6.0);
        p.put(TipoEvento.FAVORITE, 8.0);
        p.put(TipoEvento.ADD_TO_CART, 12.0);
        p.put(TipoEvento.REVIEW, 15.0);
        p.put(TipoEvento.PURCHASE, 20.0);
        p.put(TipoEvento.IMPRESSION, -0.1);
        p.put(TipoEvento.REMOVE_FROM_CART, -4.0);
        p.put(TipoEvento.DISMISS, -10.0);
        p.put(TipoEvento.NOT_INTERESTED, -25.0);
        return p;
    }
}
