package com.backend.catalogo.descubrimiento.negocio;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.backend.catalogo.descubrimiento.RazonRecomendacion;

/**
 * Qué pasó DESPUÉS de exponer cada categoría.
 *
 * <h4>Asociación temporal, no causalidad</h4>
 *
 * <p>Es la advertencia que va primero porque es la que se olvida primero. Todo
 * lo que hay aquí dice «se sirvió esto y, dentro de la ventana, el mismo sujeto
 * hizo aquello con el mismo producto». No dice que lo uno provocara lo otro.
 *
 * <p>El modelo no puede decirlo: {@code evento_interaccion.origen} es texto
 * libre que manda el cliente, no una referencia a la recomendación concreta. Un
 * visitante puede ver un carrusel, ignorarlo, buscar el producto en el buscador
 * y comprarlo; con los datos que hay, eso se cuenta como asociado. Llamarlo
 * causa sería inventar una cadena que la ingesta no garantiza, y decisiones
 * comerciales tomadas sobre eso serían peores que no medir.
 *
 * <p>Por eso ninguna palabra de esta clase dice «generado» ni «causado».
 *
 * <h4>El embudo entero, sin resumir</h4>
 *
 * <p>Se conservan las cinco etapas por separado y no hay un número único que
 * las combine. Mil vistas y cien clics parecen un éxito hasta que se mira que
 * hay cero carritos; un score que mezclara las dos cosas escondería exactamente
 * el caso que hace falta detectar.
 *
 * @param horasAtribucion la ventana con la que se asoció, que forma parte del
 *     resultado: dos informes con ventanas distintas no son comparables
 * @param minimoSujetos el piso de evidencia aplicado, el mismo de la fase 3
 */
public record InformeNegocio(
        Instant desde,
        Instant hasta,
        String modulo,
        int horasAtribucion,
        int minimoSujetos,
        List<Categoria> categorias,
        List<PorBanda> bandas,
        List<PorRazon> razones,
        Concentracion concentracion) {

    /** Si una fila se puede leer como una tasa o solo como una cuenta. */
    public enum Evidencia {
        /** No se sirvió nada de esa categoría. No hay nada que medir. */
        SIN_EXPOSICION,
        /** Se sirvió, pero detrás hay tan poca gente que una tasa mentiría. */
        INSUFICIENTE,
        /** Hay suficiente para que la tasa signifique algo. */
        SUFICIENTE
    }

    /** Las categorías elegibles que no recibieron ni una exposición. */
    public List<Categoria> categoriasSinExposicion() {
        return categorias.stream()
                .filter(c -> c.evidencia(minimoSujetos) == Evidencia.SIN_EXPOSICION)
                .toList();
    }

    /**
     * Categorías que se ven mucho y no mueven nada.
     *
     * <p>La pregunta del bloque: exposición sin interacción profunda, carrito ni
     * compra. Se exige evidencia suficiente para no señalar una categoría por
     * haber tenido dos visitas.
     */
    public List<Categoria> conExposicionYSinNegocio() {
        return categorias.stream()
                .filter(c -> c.evidencia(minimoSujetos) == Evidencia.SUFICIENTE)
                .filter(c -> c.embudo().vistas() > 0)
                .filter(c -> c.embudo().profundas() == 0
                        && c.embudo().carritos() == 0
                        && c.embudo().compras() == 0)
                .toList();
    }

    /**
     * Una categoría con su embudo.
     *
     * @param productosElegibles su catálogo vivo; el universo del que sale
     */
    public record Categoria(Long id, String nombre, long productosElegibles, Embudo embudo) {

        public Evidencia evidencia(int minimoSujetos) {
            if (embudo.servidas() == 0) {
                return Evidencia.SIN_EXPOSICION;
            }
            return embudo.sujetos() < minimoSujetos
                    ? Evidencia.INSUFICIENTE
                    : Evidencia.SUFICIENTE;
        }

        /**
         * Las tasas, SOLO si se pueden defender.
         *
         * <p>Vacío cuando la evidencia no llega al mínimo, y es deliberado que
         * sea imposible sacarlas de otro modo: una tasa calculada con cinco
         * eventos y presentada como conclusión de negocio es peor que no tener
         * la cifra, porque da una confianza que no existe.
         */
        public Optional<Tasas> tasas(int minimoSujetos) {
            return evidencia(minimoSujetos) == Evidencia.SUFICIENTE
                    ? Optional.of(embudo.tasas())
                    : Optional.empty();
        }
    }

    /**
     * Las cuentas crudas de una etapa a la siguiente.
     *
     * <p>{@code servidas} es la decisión del backend y {@code vistas} lo que el
     * navegador confirmó que entró en pantalla. No se colapsan: una categoría
     * con cien servidas y cinco vistas tiene que seguir diciendo exactamente
     * eso, porque el problema de esa categoría está en el carrusel y no en el
     * producto.
     *
     * <p>{@code clics}, {@code profundas}, {@code carritos} y {@code compras}
     * cuentan RECOMENDACIONES con una acción asociada después, no acciones
     * sueltas: es la misma unidad que usa la agregación diaria de la fase 3.
     *
     * @param sujetos cuántas personas distintas sostienen la fila. Está para lo
     *     contrario de identificar: es el piso que permite descartar una cifra
     *     sostenida por cuatro personas
     */
    public record Embudo(
            long servidas,
            long vistas,
            long clics,
            long profundas,
            long carritos,
            long compras,
            long sujetos) {

        public static Embudo vacio() {
            return new Embudo(0, 0, 0, 0, 0, 0, 0);
        }

        /**
         * Cuánto de lo servido llegó a verse.
         *
         * @return {@code null} si no se sirvió nada. Sin denominador no es 0 %,
         *     es «no se sabe», y son dos cosas muy distintas
         */
        public Double pctVisto() {
            return servidas == 0 ? null : (double) vistas / servidas;
        }

        public Tasas tasas() {
            return new Tasas(pctVisto(), sobreVistas(clics), sobreVistas(profundas),
                    sobreVistas(carritos), sobreVistas(compras));
        }

        /**
         * El denominador de todas las tasas de conducta es VISTAS.
         *
         * <p>Nunca servidas, y es la regla de la fase 3. Dividir por lo servido
         * mezcla dos preguntas —si la recomendación era buena y si el usuario
         * llegó a desplazarse hasta ella— y hunde por igual a un módulo que
         * acierta y queda al final de la página.
         */
        private Double sobreVistas(long cuantos) {
            return vistas == 0 ? null : (double) cuantos / vistas;
        }
    }

    /**
     * Las tasas, todas sobre vistas menos la primera.
     *
     * <p>Cada una puede ser {@code null}, que significa «no hay denominador».
     * Un cero aquí es un cero medido; un nulo es una ausencia de evidencia.
     */
    public record Tasas(Double pctVisto, Double ctr, Double vistaProfunda,
            Double carrito, Double compra) {
    }

    /**
     * Una categoría dentro de una banda de posición.
     *
     * <p>Responde si una categoría rinde de verdad o solo sale siempre arriba.
     * Un CTR alto en la banda 0 y nulo en la 3 no habla de la categoría: habla
     * de dónde se la coloca.
     */
    public record PorBanda(Long categoriaId, short banda, Embudo embudo) {
    }

    /**
     * Una categoría dentro de un módulo y una razón concretos.
     *
     * <p>La razón llega tal cual se anotó. Agruparlas aquí haría imposible
     * separar los dos generadores que comparten carrusel, que es justo lo que
     * la medición existe para responder.
     */
    public record PorRazon(Long categoriaId, String modulo, RazonRecomendacion razon,
            String rankerVersion, boolean conPerfil, Embudo embudo) {
    }

    /**
     * Dónde se concentran los resultados. No qué los causó.
     *
     * <p>Se mide sobre cada etapa por separado a propósito: la concentración de
     * clics y la de carritos pueden estar en categorías distintas, y esa
     * diferencia es informativa.
     */
    public record Concentracion(Reparto clics, Reparto carritos, Reparto compras) {

        public static Concentracion vacia() {
            return new Concentracion(Reparto.vacio(), Reparto.vacio(), Reparto.vacio());
        }
    }

    /**
     * @param top1 parte del total en manos de la categoría que más acumula
     * @param top5 parte en manos de las cinco que más acumulan
     */
    public record Reparto(long total, Double top1, Double top5) {

        public static Reparto vacio() {
            return new Reparto(0, null, null);
        }
    }
}
