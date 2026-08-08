package com.backend.usuarios.shared.validacion;

/** Topes de entrada compartidos por todos los controladores. */
public final class Limites {

    private Limites() {
    }

    /** Tope de `size` en endpoints paginados: sin él, ?size=999999999 agota memoria. */
    public static final int MAX_PAGINA = 100;

    /** Longitud máxima de un término de búsqueda; alimenta un LIKE '%...%'. */
    public static final int MAX_BUSQUEDA = 80;

    /** Máximo de identificadores admitidos en una consulta por lote. */
    public static final int MAX_LOTE = 200;

    /** Caracteres admitidos en una búsqueda: letras, dígitos, espacios y unos pocos signos. */
    public static final String TEXTO_BUSQUEDA = "^[\\p{L}\\p{N} .,'\\-]*$";

    /** Slug de URL: solo minúsculas, dígitos y guiones. */
    public static final String SLUG = "^[a-z0-9-]{1,120}$";
}
