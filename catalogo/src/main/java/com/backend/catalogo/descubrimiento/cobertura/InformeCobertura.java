package com.backend.catalogo.descubrimiento.cobertura;

import java.time.Instant;
import java.util.List;

/**
 * Cuánto catálogo toca el recomendador, y cuánto no toca nunca.
 *
 * <h4>Cobertura no es diversidad</h4>
 *
 * <p>Es la distinción que da sentido a todo esto. La DIVERSIDAD, que ya calcula
 * la evaluación offline, mira dentro de una lista: cuántas categorías distintas
 * hay en los doce productos de un carrusel. La COBERTURA mira el catálogo
 * entero: cuántas de las categorías que existen llegaron a aparecer alguna vez.
 *
 * <p>Un recomendador puede tener la diversidad perfecta y la cobertura por los
 * suelos a la vez, y es un caso corriente, no una rareza: si siempre sirve las
 * mismas tres categorías pero nunca repite dos productos de la misma dentro de
 * un carrusel, cada lista se ve variadísima y el resto del catálogo no existe.
 * Quien mire solo la diversidad concluirá que el sistema funciona.
 *
 * <h4>Servido y visto no son lo mismo</h4>
 *
 * <p>Se devuelven las dos y separadas. Lo servido es lo que el backend decidió
 * enseñar; lo visto es lo que el navegador confirmó que entró en pantalla. En un
 * Home largo la diferencia es enorme, y colapsarlas en una sola cifra respondería
 * mal a las dos preguntas: ni «qué está proponiendo el recomendador» ni «qué
 * llega de verdad a los ojos de alguien».
 *
 * @param desde inicio del periodo, inclusive
 * @param hasta fin del periodo, EXCLUSIVE
 * @param modulo el carrusel medido, o {@code null} si son todos
 */
public record InformeCobertura(
        Instant desde,
        Instant hasta,
        String modulo,
        Cobertura servido,
        Cobertura visto) {

    /** De dónde salen las cifras. Nunca se suman entre sí. */
    public enum Fuente {
        /** Lo que el backend decidió enseñar: {@code recomendacion_servida}. */
        SERVIDO,
        /** Lo que llegó a entrar en pantalla: {@code impresion}. */
        VISTO
    }

    /**
     * Las cifras de una fuente.
     *
     * @param facetasDeCategoria una entrada por categoría con catálogo vivo,
     *     tenga exposición o no; ordenadas de más expuesta a menos
     */
    public record Cobertura(
            Fuente fuente,
            Dimension productos,
            Dimension categorias,
            Dimension marcas,
            List<Faceta> facetasDeCategoria,
            List<Faceta> facetasDeMarca,
            Concentracion concentracion,
            List<Banda> bandas) {

        /** Las categorías elegibles que no recibieron ni una exposición. */
        public List<Faceta> categoriasSinExposicion() {
            return facetasDeCategoria.stream().filter(Faceta::sinExposicion).toList();
        }

        /** Las marcas elegibles que no recibieron ni una exposición. */
        public List<Faceta> marcasSinExposicion() {
            return facetasDeMarca.stream().filter(Faceta::sinExposicion).toList();
        }

        /**
         * Categorías que reciben mucha exposición gracias a muy pocos productos.
         *
         * <p>Saber que una categoría se lleva mil exposiciones no dice nada por
         * sí solo: puede ser una categoría grande funcionando bien, o dos
         * productos acaparándola mientras los otros cuarenta no salen nunca.
         * Son dos problemas distintos y se arreglan de manera distinta.
         *
         * @param umbral proporción de la categoría en manos de su producto más
         *     expuesto a partir de la cual se considera dominada
         */
        public List<Faceta> categoriasDominadasPorPocosProductos(double umbral) {
            return facetasDeCategoria.stream()
                    .filter(f -> f.exposiciones() > 0 && f.topProductoPct() >= umbral)
                    .toList();
        }
    }

    /**
     * Un universo y cuánto de él se tocó.
     *
     * @param elegibles el denominador: lo que PODÍA recomendarse
     * @param expuestos el numerador: lo que llegó a recomendarse
     */
    public record Dimension(long elegibles, long expuestos) {

        /**
         * La proporción tocada, entre 0 y 1.
         *
         * @return 0 si no hay universo. Sin denominador la cobertura no es 0,
         *     es «no se sabe», y quien lea esto tiene que mirar
         *     {@link #elegibles()} antes de concluir nada
         */
        public double proporcion() {
            return elegibles == 0 ? 0.0 : (double) expuestos / elegibles;
        }

        public long sinExposicion() {
            return elegibles - expuestos;
        }
    }

    /**
     * Una categoría o una marca, con su parte del reparto.
     *
     * @param topProducto exposiciones del producto más expuesto de la faceta. Es
     *     un MÁXIMO, no una identidad: permite medir la concentración interna
     *     sin guardar en ninguna parte de qué producto se trataba
     */
    public record Faceta(
            Long id,
            String nombre,
            long productosElegibles,
            long productosExpuestos,
            long exposiciones,
            long topProducto) {

        public boolean sinExposicion() {
            return exposiciones == 0;
        }

        /** Qué parte de la faceta se lleva su producto más expuesto. */
        public double topProductoPct() {
            return exposiciones == 0 ? 0.0 : (double) topProducto / exposiciones;
        }

        /** Qué parte de su propio catálogo llegó a salir. */
        public double coberturaInterna() {
            return productosElegibles == 0 ? 0.0
                    : (double) productosExpuestos / productosElegibles;
        }
    }

    /**
     * Cuánto se lo llevan las de arriba.
     *
     * <p>Sobre EXPOSICIONES y no sobre número de productos distintos. Contar
     * productos respondería «qué tamaño tiene el catálogo de esa categoría», que
     * no es la pregunta: una categoría con tres productos servidos mil veces
     * cada uno concentra el reparto aunque sean tres.
     *
     * @param top1 parte del total que se lleva la categoría más expuesta
     * @param top5 parte que se llevan las cinco más expuestas
     */
    public record Concentracion(long exposiciones, double top1, double top5) {

        public static Concentracion vacia() {
            return new Concentracion(0, 0.0, 0.0);
        }
    }

    /**
     * El reparto dentro de una banda de posición.
     *
     * <p>La banda es la de la fase 3, {@code LEAST(posicion / 3, 7)}, reutilizada
     * y no reinventada. Sirve para ver algo que el total esconde: un sistema
     * puede parecer variado y estar poniendo siempre las mismas categorías en
     * las tres primeras tarjetas, que es donde la gente mira.
     */
    public record Banda(short banda, long exposiciones, long categorias, long productos,
            double top1) {
    }
}
