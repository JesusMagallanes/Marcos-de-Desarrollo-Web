package com.backend.catalogo.descubrimiento.evaluacion;

import java.util.EnumMap;
import java.util.Map;

import com.backend.catalogo.descubrimiento.Origen;
import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;

/**
 * Una configuración de pesos con nombre, para poder comparar dos.
 *
 * <p>Existe porque la pregunta de la fase 3 —«¿esto mejoró?»— no se puede
 * responder sin poder decir *respecto a qué*. Sin una configuración
 * identificable, mover un peso es cambiar el sistema sin dejar rastro de qué
 * había antes.
 *
 * @param nombre etiqueta legible; aparece en el informe
 * @param pesoPorOrigen cuánto se cree a cada fuente
 * @param penalizacionPopularidad cuánto se castiga la sobreexposición
 */
public record ConfiguracionRanker(
        String nombre,
        Map<Origen, Double> pesoPorOrigen,
        double penalizacionPopularidad) {

    public ConfiguracionRanker {
        pesoPorOrigen = new EnumMap<>(pesoPorOrigen);
    }

    public double pesoDe(Origen origen) {
        return pesoPorOrigen.getOrDefault(origen, 0.0);
    }

    /** El mismo factor logarítmico que aplica el ranker en producción. */
    public double factorPopularidad(double exposicion) {
        if (exposicion <= 0) {
            return 1.0;
        }
        return 1.0 / (1.0 + penalizacionPopularidad * Math.log1p(exposicion));
    }

    /**
     * La configuración que está sirviendo tráfico ahora mismo.
     *
     * <p>Es la línea base contra la que se compara cualquier propuesta: sin
     * medir primero lo que ya hay, «mejor» no significa nada.
     */
    public static ConfiguracionRanker enProduccion(PesosDescubrimiento pesos) {
        Map<Origen, Double> mapa = new EnumMap<>(Origen.class);
        for (Origen origen : Origen.values()) {
            mapa.put(origen, pesos.pesoDe(origen));
        }
        return new ConfiguracionRanker(pesos.rankerVersion(), mapa,
                pesos.getPenalizacionPopularidad());
    }

    /** Una variante de esta, cambiando un solo origen. Para explorar el espacio. */
    public ConfiguracionRanker con(String nombreNuevo, Origen origen, double peso) {
        Map<Origen, Double> mapa = new EnumMap<>(pesoPorOrigen);
        mapa.put(origen, peso);
        return new ConfiguracionRanker(nombreNuevo, mapa, penalizacionPopularidad);
    }
}
