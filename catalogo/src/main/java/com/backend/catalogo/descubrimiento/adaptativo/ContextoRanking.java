package com.backend.catalogo.descubrimiento.adaptativo;

/**
 * Dónde y a quién se está recomendando.
 *
 * <p>La fase 3 tenía un solo juego de pesos para todo, y eso es una simplificación
 * que se nota. Quien abre la ficha de un monitor está haciendo una pregunta
 * concreta —«¿y con esto qué va?»— y quien abre el Home no está preguntando
 * nada. Servir a los dos con la misma mezcla significa acertar a medias en los
 * dos sitios.
 *
 * <p>Lo mismo con el perfil: a quien llega por primera vez no se le puede
 * personalizar, así que insistir en la señal personal solo reparte ceros. Ahí
 * lo que funciona es lo popular y lo que se mueve.
 *
 * <p>Son cuatro combinaciones y no más. Multiplicar contextos es fácil y lleva
 * a un sistema que nadie puede calibrar porque nunca hay datos suficientes para
 * ninguna casilla.
 *
 * @param superficie desde qué pantalla se pide
 * @param conPerfil si hay historial del que tirar
 */
public record ContextoRanking(Superficie superficie, boolean conPerfil) {

    /** Desde dónde se pide la recomendación. */
    public enum Superficie {
        /** La portada: nadie ha preguntado nada concreto. */
        HOME,
        /** La ficha de un producto: la pregunta la marca el producto. */
        FICHA
    }

    public static ContextoRanking home(boolean conPerfil) {
        return new ContextoRanking(Superficie.HOME, conPerfil);
    }

    public static ContextoRanking ficha(boolean conPerfil) {
        return new ContextoRanking(Superficie.FICHA, conPerfil);
    }

    /**
     * La clave con la que se buscan los pesos de este contexto.
     *
     * <p>Texto y no un enum de cuatro valores a propósito: así la configuración
     * se puede escribir en {@code application.properties} —
     * {@code descubrimiento.adaptativo.pesos.FICHA_CON_PERFIL.COHORTE=1.2}— sin
     * que haya que tocar código para probar una calibración.
     */
    public String clave() {
        return superficie.name() + (conPerfil ? "_CON_PERFIL" : "_SIN_PERFIL");
    }
}
