package com.backend.catalogo.descubrimiento.adaptativo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.backend.catalogo.descubrimiento.Origen;

import lombok.Getter;
import lombok.Setter;

/**
 * La calibración del ranker adaptativo, aparte de la de la fase 3.
 *
 * <p>Aparte a propósito, y no por orden: es lo que hace posible el rollback.
 * {@code PesosDescubrimiento} sigue intacto y sigue sirviendo al ranker
 * determinista, así que apagar esto con {@code activo=false} devuelve el sistema
 * exactamente al comportamiento de la fase 3 sin desplegar nada.
 *
 * <h4>Los pesos por contexto</h4>
 *
 * <p>Cuatro combinaciones: Home y ficha, con perfil y sin él. Los valores de
 * partida no son una verdad, son una hipótesis razonada que ahora —por primera
 * vez— se puede medir:
 *
 * <ul>
 *   <li><b>Sin perfil</b> la señal personal reparte ceros, así que mandan lo
 *       popular y lo que se mueve, y se explora más porque no hay nada que
 *       perder.</li>
 *   <li><b>En la ficha</b> la pregunta la marca el producto: pesa el contenido
 *       y la co-visita, y se explora poco porque desviar a alguien que está
 *       mirando algo concreto es más molesto que útil.</li>
 *   <li><b>En el Home con perfil</b> es donde el descubrimiento tiene sitio, y
 *       donde la exploración se paga sola.</li>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "descubrimiento.adaptativo")
@Getter
@Setter
public class PesosAdaptativos {

    /**
     * El interruptor del rollback.
     *
     * <p>Con {@code false} el sistema usa el ranker de la fase 3 y nada de esta
     * fase toca una recomendación. Es una propiedad y no una compilación
     * condicional para que volver atrás sea reiniciar, no desplegar.
     */
    private boolean activo = true;

    /** Etiqueta legible de esta calibración. La huella la completa. */
    private String etiqueta = "v4.0";

    /**
     * Pesos por contexto y origen, con las claves de {@link ContextoRanking}.
     *
     * <p>Un contexto que falte cae a {@link #pesosPorDefecto}, que son los de la
     * fase 3. Faltar no es un error: es la respuesta correcta mientras nadie
     * haya calibrado ese caso.
     */
    private Map<String, Map<Origen, Double>> pesos = calibracionInicial();

    /** Lo que se usa cuando un contexto no tiene calibración propia. */
    private Map<Origen, Double> pesosPorDefecto = new LinkedHashMap<>(Map.of(
            Origen.PERSONAL, 1.0,
            Origen.COHORTE, 0.8,
            Origen.GEO, 0.5,
            Origen.TENDENCIA, 0.4,
            Origen.EXPLORACION, 0.3));

    /**
     * Cuánto se castiga la exposición reciente, por contexto.
     *
     * <p>Separado de los pesos porque no es una señal más: es un freno. Mezclarlo
     * con las señales positivas haría que subir un peso pudiera anularlo sin que
     * nadie se diera cuenta.
     */
    private double penalizacionExposicion = 0.35;

    /* ══════════════ Exploración ══════════════ */

    /**
     * Proporción de huecos reservados a explorar, por fuerza del perfil.
     *
     * <p>Varía con lo que el sistema sabe: a quien no conoce, explorar es lo
     * único que puede hacer; a quien conoce bien, explorar es un coste que se
     * paga a cambio de no encerrarlo. Nunca llega a 1: un carrusel entero de
     * apuestas no es descubrimiento, es ruido.
     */
    private double exploracionSinPerfil = 0.40;
    private double exploracionPerfilEscaso = 0.25;
    private double exploracionPerfilFuerte = 0.10;

    /** Eventos a partir de los cuales se considera un perfil fuerte. */
    private int umbralPerfilFuerte = 8;

    /* ══════════════ Experimento ══════════════ */

    /** Nombre del experimento en curso. Entra en el hash de asignación. */
    private String experimento = "v4-inicial";

    /** Porcentaje de sujetos que reciben la variante. 0 = nadie, 100 = todos. */
    private int porcentajeVariante = 50;

    /** Pesos de la variante. Si está vacío, la variante es igual al control. */
    private Map<String, Map<Origen, Double>> pesosVariante = new LinkedHashMap<>();

    /* ══════════════ Consulta ══════════════ */

    public Map<Origen, Double> pesosDe(ContextoRanking contexto, boolean esVariante) {
        Map<String, Map<Origen, Double>> tabla = esVariante && !pesosVariante.isEmpty()
                ? pesosVariante
                : pesos;
        return tabla.getOrDefault(contexto.clave(), pesosPorDefecto);
    }

    public double pesoDe(ContextoRanking contexto, Origen origen, boolean esVariante) {
        Map<Origen, Double> mapa = pesosDe(contexto, esVariante);
        return mapa.getOrDefault(origen, pesosPorDefecto.getOrDefault(origen, 0.0));
    }

    /** El presupuesto de exploración que toca, entre 0 y 1. */
    public double presupuestoExploracion(boolean conPerfil, int eventosDelPerfil) {
        if (!conPerfil || eventosDelPerfil <= 0) {
            return exploracionSinPerfil;
        }
        return eventosDelPerfil >= umbralPerfilFuerte
                ? exploracionPerfilFuerte
                : exploracionPerfilEscaso;
    }

    /**
     * La versión de esta calibración, con huella.
     *
     * <p>Misma idea que en la fase 3 y por la misma razón: una etiqueta a mano
     * se olvida de subir y entonces dos configuraciones distintas comparten
     * nombre, que es peor que no versionar porque la comparación parece válida.
     *
     * <p>Aquí entra TODO lo que cambia el resultado: los pesos de cada contexto,
     * los de la variante, el freno de exposición y los presupuestos de
     * exploración. Se ordena antes de resumir para que dos mapas con el mismo
     * contenido en distinto orden den la misma huella — si no, la versión
     * cambiaría al reiniciar y la comparación se rompería sin que nadie
     * entendiera por qué.
     */
    public String version() {
        StringBuilder huella = new StringBuilder();
        volcar(huella, "base", pesos);
        volcar(huella, "variante", pesosVariante);
        volcar(huella, "defecto", Map.of("*", pesosPorDefecto));
        huella.append("exp=").append(penalizacionExposicion)
                .append(";x0=").append(exploracionSinPerfil)
                .append(";x1=").append(exploracionPerfilEscaso)
                .append(";x2=").append(exploracionPerfilFuerte)
                .append(";pct=").append(porcentajeVariante);

        return etiqueta + "-" + resumir(huella.toString());
    }

    private void volcar(StringBuilder destino, String prefijo,
            Map<String, Map<Origen, Double>> tabla) {
        destino.append(prefijo).append('{');
        for (Map.Entry<String, Map<Origen, Double>> contexto : new TreeMap<>(tabla).entrySet()) {
            destino.append(contexto.getKey()).append(':');
            for (Origen origen : Origen.values()) {
                Double peso = contexto.getValue().get(origen);
                if (peso != null) {
                    destino.append(origen.name()).append('=').append(peso).append(',');
                }
            }
            destino.append(';');
        }
        destino.append('}');
    }

    private String resumir(String texto) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(texto.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException imposible) {
            // SHA-256 lo exige la especificacion de Java desde siempre.
            throw new IllegalStateException(imposible);
        }
    }

    private static Map<String, Map<Origen, Double>> calibracionInicial() {
        Map<String, Map<Origen, Double>> tabla = new LinkedHashMap<>();

        // Home con perfil: es donde el descubrimiento tiene sitio.
        tabla.put("HOME_CON_PERFIL", new HashMap<>(Map.of(
                Origen.PERSONAL, 1.0,
                Origen.COHORTE, 0.9,
                Origen.GEO, 0.5,
                Origen.TENDENCIA, 0.4,
                Origen.EXPLORACION, 0.35)));

        // Home sin perfil: lo personal reparte ceros, asi que manda lo que se mueve.
        tabla.put("HOME_SIN_PERFIL", new HashMap<>(Map.of(
                Origen.PERSONAL, 0.2,
                Origen.COHORTE, 0.5,
                Origen.GEO, 0.8,
                Origen.TENDENCIA, 1.0,
                Origen.EXPLORACION, 0.5)));

        /*
         * Ficha: aqui la conducta ajena vale MAS que el gusto propio.
         *
         * Es la diferencia de fondo con el Home y conviene decirla clara. En la
         * portada la pregunta es «¿que te gusta?» y manda el perfil. En una
         * ficha la pregunta la hace el producto: «¿que va con esto?». Quien
         * mira una impresora no quiere que le ofrezcan monitores porque los
         * mire mucho; quiere el toner. Y eso solo lo sabe la co-visita.
         *
         * Lo de zona y tendencia se hunde por el mismo motivo: que algo se lleve
         * en Ica no responde nada sobre la impresora que hay en pantalla.
         */
        tabla.put("FICHA_CON_PERFIL", new HashMap<>(Map.of(
                Origen.PERSONAL, 0.8,
                Origen.COHORTE, 1.0,
                Origen.GEO, 0.2,
                Origen.TENDENCIA, 0.2,
                Origen.EXPLORACION, 0.15)));

        // Ficha sin perfil: lo personal es casi ruido, asi que pesa aun menos.
        tabla.put("FICHA_SIN_PERFIL", new HashMap<>(Map.of(
                Origen.PERSONAL, 0.7,
                Origen.COHORTE, 1.0,
                Origen.GEO, 0.2,
                Origen.TENDENCIA, 0.3,
                Origen.EXPLORACION, 0.15)));

        return tabla;
    }
}
