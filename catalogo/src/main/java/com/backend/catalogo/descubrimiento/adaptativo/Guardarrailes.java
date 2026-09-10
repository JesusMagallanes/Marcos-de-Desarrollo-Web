package com.backend.catalogo.descubrimiento.adaptativo;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.backend.catalogo.descubrimiento.evaluacion.ResultadoEvaluacion;

import lombok.Getter;
import lombok.Setter;

/**
 * Lo que impide que una variante gane por el motivo equivocado.
 *
 * <p>Optimizar clics es la forma más rápida de estropear una tienda, y no es
 * una opinión: es adónde lleva la aritmética. La lista que más clics recibe es
 * la de los diez superventas, porque son lo que la gente ya conocía y habría
 * encontrado sola. Un sistema que persiga esa cifra acabará enseñando el 5 % del
 * catálogo, sin descubrirle nada a nadie, mientras el panel enseña una curva que
 * sube.
 *
 * <p>Por eso una variante no se promueve por ganar en una métrica, sino por no
 * perder en ninguna que importe. La relevancia tiene que subir, y la cobertura,
 * la diversidad y la novedad no pueden desplomarse a cambio. Los límites son
 * configurables porque son una decisión de producto, no un resultado.
 *
 * <p>Y por encima de todo eso está la muestra. Con veinte sujetos, cualquier
 * diferencia es ruido; declarar un ganador ahí es peor que no medir, porque
 * pone un número donde no hay conocimiento.
 */
@Component
@ConfigurationProperties(prefix = "descubrimiento.guardarrailes")
@Getter
@Setter
public class Guardarrailes {

    /** Sujetos mínimos por brazo antes de mirar siquiera las diferencias. */
    private int muestraMinima = 200;

    /** Cuánto tiene que mejorar la relevancia para que compense el cambio. */
    private double mejoraMinimaNdcg = 0.01;

    /** Caída máxima tolerada en cada objetivo que no es relevancia. */
    private double caidaMaximaCobertura = 0.10;
    private double caidaMaximaDiversidad = 0.10;
    private double caidaMaximaNovedad = 0.10;

    /** Cuánta repetición se acepta antes de considerar la lista degradada. */
    private double repeticionMaxima = 0.05;

    /** El veredicto, con los motivos. Nunca un simple sí o no. */
    public enum Veredicto {
        /** Mejora sin romper nada: se puede promover. */
        PROMOVER,
        /** No hay datos suficientes. NO es un empate. */
        INCONCLUSIVO,
        /** Mejora en algo y rompe algo, o simplemente no mejora. */
        NO_PROMOVER
    }

    /**
     * @param veredicto qué se puede hacer con la variante
     * @param motivos por qué; siempre poblado, también cuando se promueve
     */
    public record Dictamen(Veredicto veredicto, List<String> motivos) {

        public boolean promovible() {
            return veredicto == Veredicto.PROMOVER;
        }
    }

    /**
     * Compara la variante contra el control.
     *
     * <p>El orden de las comprobaciones importa: primero la muestra, porque sin
     * ella lo demás no se puede ni mirar; después las caídas, porque un
     * desplome descalifica por bueno que sea el resto; y solo al final si de
     * verdad mejora.
     */
    public Dictamen evaluar(ResultadoEvaluacion control, ResultadoEvaluacion variante) {
        List<String> motivos = new ArrayList<>();

        if (control.sujetosEvaluados() < muestraMinima
                || variante.sujetosEvaluados() < muestraMinima) {
            motivos.add(String.format(
                    "muestra insuficiente: control %d, variante %d, mínimo %d por brazo",
                    control.sujetosEvaluados(), variante.sujetosEvaluados(), muestraMinima));
            return new Dictamen(Veredicto.INCONCLUSIVO, motivos);
        }

        comprobarCaida(motivos, "cobertura", control.cobertura(), variante.cobertura(),
                caidaMaximaCobertura);
        comprobarCaida(motivos, "diversidad de categoría",
                control.diversidadCategoria(), variante.diversidadCategoria(),
                caidaMaximaDiversidad);
        comprobarCaida(motivos, "novedad", control.novedad(), variante.novedad(),
                caidaMaximaNovedad);

        if (variante.repeticion() > repeticionMaxima) {
            motivos.add(String.format(
                    "repetición %.3f por encima del máximo %.3f: estaría devolviendo"
                            + " lo que la persona ya vio",
                    variante.repeticion(), repeticionMaxima));
        }

        double mejora = variante.ndcg10() - control.ndcg10();
        if (mejora < mejoraMinimaNdcg) {
            motivos.add(String.format(
                    "NDCG@10 sube %.4f, por debajo del mínimo %.4f: no compensa el cambio",
                    mejora, mejoraMinimaNdcg));
        }

        if (!motivos.isEmpty()) {
            return new Dictamen(Veredicto.NO_PROMOVER, motivos);
        }

        motivos.add(String.format(
                "NDCG@10 %.4f → %.4f; cobertura %.3f → %.3f; novedad %.3f → %.3f",
                control.ndcg10(), variante.ndcg10(),
                control.cobertura(), variante.cobertura(),
                control.novedad(), variante.novedad()));
        return new Dictamen(Veredicto.PROMOVER, motivos);
    }

    /**
     * Una caída relativa, no absoluta.
     *
     * <p>Perder cinco céntesimas de cobertura no significa lo mismo partiendo de
     * 0,80 que de 0,08. Lo que interesa es qué proporción se pierde.
     */
    private void comprobarCaida(List<String> motivos, String nombre,
            double antes, double despues, double maxima) {
        if (antes <= 0) {
            return;
        }
        double caida = (antes - despues) / antes;
        if (caida > maxima) {
            motivos.add(String.format(
                    "%s cae un %.1f %% (%.3f → %.3f), por encima del %.1f %% tolerado",
                    nombre, caida * 100, antes, despues, maxima * 100));
        }
    }
}
