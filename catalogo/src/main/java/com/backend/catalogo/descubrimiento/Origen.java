package com.backend.catalogo.descubrimiento;

/**
 * De dónde viene una recomendación.
 *
 * <p>Viaja en la respuesta a propósito. Presentar una tendencia general como si
 * fuera personal es la forma más rápida de que la plataforma se sienta falsa
 * y, cuando acierta por casualidad, de que se sienta vigilante. La interfaz
 * necesita poder decir «lo más visto en Ica» y no «según tus intereses» cuando
 * el dato es lo primero.
 */
public enum Origen {

    /** Solo del historial de esta persona. */
    PERSONAL,
    /** Patrones agregados de gente parecida. Nunca identidades. */
    COHORTE,
    /** Su zona, sin usar su perfil. */
    GEO,
    /** Lo que se mueve en general. */
    TENDENCIA,
    /** Presupuesto de exploración: puede fallar, y se avisa. */
    EXPLORACION
}
