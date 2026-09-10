package com.backend.catalogo.descubrimiento.evaluacion;

import java.util.Map;

/**
 * Lo que una configuración consiguió sobre un corte, sin resumirlo a un número.
 *
 * <p>Los objetivos se mantienen SEPARADOS a propósito. Un recomendador que
 * maximiza aciertos y solo enseña los diez superventas puntúa muy bien en
 * relevancia y está destruyendo la tienda: el 95 % del catálogo se vuelve
 * invisible y nadie descubre nada. Meterlo todo en una cifra permite que ese
 * desastre se esconda detrás de una media, y por eso aquí no hay ninguna cifra
 * que lo resuma salvo la que se calcula aparte y diciendo cómo.
 *
 * @param configuracion qué pesos produjeron esto
 * @param sujetosEvaluados cuántos sujetos entraron; con pocos, nada de lo demás
 *     significa gran cosa y hay que mirarlo antes que las tasas
 * @param recall5 de lo que el sujeto hizo después, cuánto había en el top 5
 * @param ndcg5 lo mismo, premiando acertar ARRIBA; un acierto en la décima
 *     posición vale menos porque casi nadie llega
 * @param cobertura porción del catálogo elegible que llegó a recomendarse
 * @param diversidadCategoria categorías distintas por lista, entre 0 y 1
 * @param diversidadMarca marcas distintas por lista
 * @param novedad cuánto se aleja de los superventas; 1 = nada popular
 * @param repeticion porción de recomendaciones que el sujeto ya había visto
 * @param coberturaColaborativa porción de sujetos que llegaron a recibir alguna
 *     recomendación con evidencia colaborativa detrás
 * @param recallPorHistorial recall separado por cuánta historia tenía el sujeto
 */
public record ResultadoEvaluacion(
        String configuracion,
        int sujetosEvaluados,
        double recall5,
        double recall10,
        double ndcg5,
        double ndcg10,
        double cobertura,
        double diversidadCategoria,
        double diversidadMarca,
        double novedad,
        double repeticion,
        double coberturaColaborativa,
        Map<Historial, Double> recallPorHistorial) {

    /** Cuánta historia tenía el sujeto en el corte. */
    public enum Historial {
        /** Ninguna: el arranque en frío de verdad. */
        SIN_HISTORIAL,
        /** Poca, por debajo del umbral de confianza. */
        ESCASO,
        /** Suficiente para que la personalización tenga sentido. */
        SUFICIENTE
    }

    /**
     * Un número para ordenar candidatas, calculado a la vista.
     *
     * <p>Existe solo porque comparar veinte configuraciones a ojo es inviable, y
     * está documentado aquí y no escondido: 60 % relevancia, 20 % cobertura,
     * 20 % novedad. Los porcentajes son una decisión de producto, no un
     * resultado: dicen que se prefiere acertar, pero no a cualquier precio.
     *
     * <p>Nunca debe sustituir a mirar las métricas por separado. Dos
     * configuraciones con el mismo global pueden ser sistemas muy distintos.
     */
    public double indiceGlobal() {
        return 0.60 * ndcg10 + 0.20 * cobertura + 0.20 * novedad;
    }
}
