package com.backend.web_gateway.config;

import java.util.regex.Pattern;

/**
 * Cuánto tiempo puede el navegador quedarse con cada fichero del bundle.
 *
 * <h4>El problema que arregla</h4>
 *
 * <p>Aquí no se cacheaba <b>nada</b>, ni siquiera los bundles con hash, y eso se
 * comprobó levantando el gateway y mirando las respuestas: todas salían con el
 * {@code no-cache, no-store, max-age=0} que Spring Security pone por defecto.
 *
 * <p>Había una línea que parecía encargarse del asunto —{@code
 * spring.web.resources.cache.cachecontrol.max-age=31536000}— con el comentario
 * «cache larga para los assets con hash en el nombre; el index.html no se
 * cachea». No hacía ninguna de las dos cosas: Boot aplica esa propiedad a SUS
 * manejadores de recursos autoconfigurados, y el que sirve el bundle lo
 * registra {@link SpaConfig} por su cuenta. La propiedad no llegaba a tocarlo.
 *
 * <p>El efecto era el contrario del que sugiere el nombre de esta clase: cada
 * visita volvía a descargar el bundle entero, unos 830 kB, porque {@code
 * no-store} significa «no lo guardes».
 *
 * <p>Y en {@code nginx.conf}, que es la otra forma de servir lo mismo, pasaba lo
 * opuesto: las imágenes de {@code /Img/} las copia Angular tal cual, <b>sin
 * hash</b>, y estaban marcadas {@code immutable} un año. El navegador ni
 * siquiera pregunta si cambiaron —{@code immutable} es exactamente eso— así que
 * reemplazar un logo no lo veía nadie nunca.
 *
 * <h4>La regla</h4>
 *
 * <p>Lo que decide no es la extensión sino <b>si el nombre cambia cuando cambia
 * el contenido</b>, que es la única condición que hace segura una caché eterna.
 * Angular firma los ficheros que compila (`main-XW7WSHRM.js`) y no toca los que
 * copia de {@code public/}. Por eso se busca el hash en el nombre en vez de
 * confiar en una lista de carpetas: si mañana el build cambia de sitio, un
 * fichero sin firmar se revalida —que es molesto pero correcto— en lugar de
 * quedarse congelado un año.
 *
 * <p>Estas mismas tres reglas están en {@code frontend/nginx.conf}, porque el
 * bundle se sirve por los dos sitios según cómo se despliegue. Si se cambia una,
 * hay que cambiar la otra.
 */
public final class PoliticaCache {

    /**
     * El nombre lleva la firma del contenido: si el contenido cambia, cambia el
     * nombre y la URL es otra. Se puede cachear para siempre sin preguntar.
     */
    public static final String INMUTABLE = "public, max-age=31536000, immutable";

    /**
     * Estáticos que conservan su nombre entre versiones. Se cachean, pero el
     * navegador vuelve a preguntar cada hora; si no cambió, el servidor
     * responde 304 sin cuerpo y la respuesta cuesta unos cientos de bytes.
     */
    public static final String REVALIDAR = "public, max-age=3600, must-revalidate";

    /**
     * {@code no-cache} NO significa «no lo guardes» —eso es {@code no-store},
     * que es justo lo que salía antes de aquí para todo—: significa «guárdalo,
     * pero pregunta antes de usarlo». Es lo que hace falta para index.html: un
     * despliegue se ve al instante y, mientras no haya cambios, la respuesta es
     * un 304 de cero bytes.
     */
    public static final String SIN_CACHE = "no-cache";

    /**
     * Firma que Angular añade al compilar, con {@code outputHashing: all}:
     * {@code main-XW7WSHRM.js}, {@code styles-3TCYNE4V.css}. Ocho o más
     * caracteres en mayúscula o dígitos, justo antes de la extensión.
     */
    private static final Pattern CON_HASH =
            Pattern.compile(".*-[A-Z0-9]{8,}\\.[a-z0-9]+$");

    /** Rutas que no son ficheros del bundle: las atiende el gateway o un servicio. */
    private static final Pattern NO_ES_ESTATICO =
            Pattern.compile("^/(api|actuator|oauth2|login)(/.*)?$");

    private PoliticaCache() {
    }

    /**
     * @param ruta la ruta de la petición, empezando por {@code /}
     * @return el valor de {@code Cache-Control}, o {@code null} si esta ruta no
     *         es un fichero estático y no hay que ponerle ninguno
     */
    public static String para(String ruta) {
        if (ruta == null || ruta.isBlank() || NO_ES_ESTATICO.matcher(ruta).matches()) {
            return null;
        }

        String nombre = ruta.substring(ruta.lastIndexOf('/') + 1);

        // Sin extensión es una ruta del router de Angular (/carrito, /perfil...),
        // y lo que se responde ahí es index.html: nunca se cachea.
        if (!nombre.contains(".")) {
            return SIN_CACHE;
        }
        if (nombre.equals("index.html")) {
            return SIN_CACHE;
        }
        if (CON_HASH.matcher(nombre).matches()) {
            return INMUTABLE;
        }
        return REVALIDAR;
    }
}
