package com.backend.catalogo.shared.validacion;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A03: limpieza del texto ANTES de que llegue a la base de datos.
 *
 * La validación (`@Size`, `@Pattern`, `@NotBlank`) decide si una entrada se
 * acepta o se rechaza; esto decide en qué forma se guarda lo aceptado. Son cosas
 * distintas y hacen falta las dos: un nombre de producto puede pasar el `@Size` y
 * aun así traer un carácter de dirección bidireccional que en el listado del
 * admin hace que "Laptop <b>" se pinte al revés, o un cero-ancho que crea dos
 * marcas visualmente idénticas saltándose el UNIQUE.
 *
 * Qué hace y por qué:
 *
 * - Normaliza a NFC. "á" se puede escribir como un carácter o como 'a' + acento
 *   combinante; sin normalizar son dos cadenas distintas para el UNIQUE y para
 *   cualquier comparación, y sirven para duplicar registros "iguales".
 * - Quita caracteres de control e invisibles (bidi, cero-ancho, BOM). No aportan
 *   nada legítimo en un nombre y son la base de la suplantación visual.
 * - Colapsa espacios y recorta. Evita que "  Asus  " y "Asus" convivan.
 *
 * Lo que NO hace: escapar HTML. El texto se guarda tal cual el usuario lo
 * escribió y se escapa AL PINTARLO (Angular interpola escapando por defecto, y
 * el chatbot escapa a mano lo que mete en HTML). Escapar al guardar corrompe el
 * dato — un producto llamado "Cable 3 & 4" acabaría almacenado como
 * "Cable 3 &amp; 4" y saldría así en las facturas.
 */
public final class Saneador {

    private Saneador() {
    }

    /**
     * Controles ASCII salvo tabulador y salto de línea, más los invisibles que se
     * usan para suplantar. Deliberadamente NO incluye toda la categoría \p{Cf}:
     * ahí vive U+200D (ZWJ), que es lo que une los emoji compuestos.
     */
    private static final Pattern PELIGROSOS = Pattern.compile(
            "[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F"
                    + "\\u200B\\u200C\\u200E\\u200F"
                    + "\\u202A-\\u202E\\u2066-\\u2069"
                    + "\\uFEFF]");

    private static final Pattern ESPACIOS = Pattern.compile("[ \\t\\x0B\\f\\u00A0\\u2000-\\u200A\\u3000]+");

    private static final Pattern SALTOS_SOBRANTES = Pattern.compile("\\n{3,}");

    /** Esquemas que jamás deben acabar en un atributo src/href. */
    private static final Pattern ESQUEMA_PELIGROSO = Pattern.compile(
            "^\\s*(javascript|data|vbscript|file|blob):", Pattern.CASE_INSENSITIVE);

    /**
     * `data:` admitido SOLO para imágenes rasterizadas (nunca SVG, que puede
     * llevar scripts). El formato es `data:image/png;base64,…`; tras el tipo va
     * siempre `;` (base64) o `,`.
     */
    private static final Pattern DATA_IMAGEN_SEGURA = Pattern.compile(
            "^data:image/(?:png|jpe?g|gif|webp|bmp|avif|x-icon)[;,]",
            Pattern.CASE_INSENSITIVE);

    /**
     * Texto de una sola línea: nombres, títulos, direcciones. Los saltos de línea
     * se convierten en espacio.
     */
    public static String texto(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = base(valor).replace('\n', ' ');
        limpio = ESPACIOS.matcher(limpio).replaceAll(" ").trim();
        return limpio;
    }

    /**
     * Texto largo que conserva los saltos de línea: descripciones y
     * especificaciones. Se normalizan los finales de línea y se cortan las rachas
     * de más de dos saltos seguidos.
     */
    public static String textoMultilinea(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = base(valor);
        limpio = ESPACIOS.matcher(limpio).replaceAll(" ");
        limpio = SALTOS_SOBRANTES.matcher(limpio).replaceAll("\n\n");
        // Espacios sueltos al principio o final de cada línea.
        limpio = limpio.lines().map(String::strip).reduce((a, b) -> a + "\n" + b).orElse("");
        return limpio.trim();
    }

    /** Convierte a null el texto que queda vacío tras limpiar. */
    public static String textoONulo(String valor) {
        String limpio = texto(valor);
        return limpio == null || limpio.isEmpty() ? null : limpio;
    }

    public static String textoMultilineaONulo(String valor) {
        String limpio = textoMultilinea(valor);
        return limpio == null || limpio.isEmpty() ? null : limpio;
    }

    /** Correo: además de limpiar, a minúsculas, que es como se compara. */
    public static String email(String valor) {
        String limpio = texto(valor);
        return limpio == null ? null : limpio.toLowerCase(Locale.ROOT);
    }

    /**
     * true si la URL es segura para un atributo src: http, https, una ruta del
     * propio sitio o un `data:image/` rasterizado (base64) de un ícono subido
     * desde el panel. Se usa desde la validación para responder 400, no para
     * descartar en silencio.
     *
     * `data:` se admite SOLO para imágenes rasterizadas (PNG, JPG, GIF, WebP…):
     * un `data:text/html`, un `data:image/svg+xml` con script o cualquier otro
     * esquema sigue rechazado.
     */
    public static boolean urlSegura(String valor) {
        if (valor == null || valor.isBlank()) {
            return true;
        }
        String limpio = texto(valor);
        String enMinusculas = limpio.toLowerCase(Locale.ROOT);
        if (ESQUEMA_PELIGROSO.matcher(limpio).find()
                && !DATA_IMAGEN_SEGURA.matcher(enMinusculas).find()) {
            return false;
        }
        return limpio.startsWith("/") || limpio.startsWith("http://") || limpio.startsWith("https://")
                || DATA_IMAGEN_SEGURA.matcher(enMinusculas).find();
    }

    private static String base(String valor) {
        String normalizado = Normalizer.normalize(valor, Normalizer.Form.NFC);
        String sinSaltosRaros = normalizado.replace("\r\n", "\n").replace('\r', '\n');
        return PELIGROSOS.matcher(sinSaltosRaros).replaceAll("");
    }
}
