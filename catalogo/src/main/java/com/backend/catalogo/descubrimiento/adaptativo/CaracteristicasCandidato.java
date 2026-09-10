package com.backend.catalogo.descubrimiento.adaptativo;

import com.backend.catalogo.descubrimiento.Origen;
import com.backend.catalogo.descubrimiento.RazonRecomendacion;

/**
 * Lo que se sabe de un candidato en el momento de ordenarlo.
 *
 * <p>Hasta la fase 3 esto estaba implícito: cada generador devolvía un número y
 * el ranker lo ponderaba por su origen. Funciona, pero no se puede explicar por
 * qué un candidato ganó a otro más allá de «tenía más score», ni se puede
 * cambiar la fórmula sin cambiar el código que la calcula.
 *
 * <p>Sacarlo a un vector explícito es lo que permite las tres cosas que pide
 * esta fase: probar fórmulas distintas sobre las mismas entradas, explicar una
 * decisión mirando sus partes, y comprobar en una prueba que la aritmética es
 * la que se cree.
 *
 * <h4>Solo lo que ya se sabe</h4>
 *
 * <p>Ninguna de estas cifras necesita una consulta nueva por candidato. Las de
 * origen vienen del propio generador; la exposición sale del recuento por lotes
 * que ya existía para castigar la popularidad. Una característica que obligara
 * a preguntar a la base por cada tarjeta convertiría el Home en N+1, y eso no
 * lo compensa ninguna mejora de ranking.
 *
 * @param itemId el producto
 * @param categoriaId para la diversidad; puede faltar
 * @param marcaId para la diversidad; puede faltar
 * @param origen qué familia de señal lo propuso
 * @param razon el generador concreto, que es más fino que el origen
 * @param scorePersonal afinidad con el perfil, normalizada
 * @param scoreColaborativo evidencia de conducta ajena, normalizada
 * @param scoreTendencia lo que se mueve, normalizado
 * @param exposicionReciente veces que el sistema lo ha enseñado esta semana
 * @param novedad 1 = nadie lo ha visto; 0 = es el más expuesto de la tanda
 * @param posicionOriginal dónde quedó antes de aplicar nada, para poder medir
 *     cuánto mueve el ranker y no confundirlo con el sesgo de posición
 */
public record CaracteristicasCandidato(
        Long itemId,
        Long categoriaId,
        Long marcaId,
        Origen origen,
        RazonRecomendacion razon,
        double scorePersonal,
        double scoreColaborativo,
        double scoreTendencia,
        long exposicionReciente,
        double novedad,
        int posicionOriginal) {

    /**
     * El valor de la señal que corresponde a un origen.
     *
     * <p>Un candidato lleva su score en la casilla de su familia y ceros en las
     * demás. No es un desperdicio: es lo que permite que la fórmula sea la misma
     * para todos y que cambiar un peso tenga el efecto que se espera.
     */
    public double scoreDe(Origen delOrigen) {
        return switch (delOrigen) {
            case PERSONAL -> scorePersonal;
            case COHORTE -> scoreColaborativo;
            case TENDENCIA, GEO -> scoreTendencia;
            case EXPLORACION -> 0.0;
        };
    }
}
