package com.backend.catalogo.descubrimiento;

/**
 * Por qué este producto y no otro.
 *
 * <p>Es distinto de {@link Origen}, y la diferencia importa. `Origen` es lo que
 * se le dice al usuario —«según tus intereses», «lo más visto en tu zona»— y
 * por eso es corto y general. Esto es lo que se guarda por dentro: qué
 * generador propuso al candidato y, por tanto, qué evidencia lo sostiene.
 *
 * <p>Sirve para dos cosas que hoy no se pueden hacer sin ello. La primera es
 * depurar: cuando una recomendación es absurda, lo primero que hay que saber es
 * de dónde salió, y sin esto hay que reconstruirlo a mano. La segunda es medir:
 * qué generador acierta se mide contando clics POR razón, y sin el código no se
 * puede atribuir nada.
 *
 * <p>No viaja al navegador. Los textos que ve la gente siguen siendo los del
 * módulo, generales y naturales; decir «personas como tú compraron esto» con la
 * evidencia que hay hoy sería, además de invasivo, mentira.
 */
public enum RazonRecomendacion {

    /** Sale del perfil de esta persona: sus categorías, marcas y atributos. */
    PERSONAL_INTEREST,

    /** Se parece por ficha a algo que miró: misma categoría, atributos comunes. */
    CONTENT_SIMILAR,

    /** Quien se interesó por lo que ella miró, se interesó también por esto. */
    CO_VIEWED,

    /** Lo descubrió gente con un perfil parecido. Nunca se dice quién. */
    SIMILAR_SUBJECT,

    /** Se está moviendo en su zona, sin mirar su perfil. */
    LOCAL_TREND,

    /** Presupuesto de exploración: deliberadamente fuera de lo conocido. */
    EXPLORATION,

    /** Arranque en frío: no hay nada personal que ofrecer todavía. */
    POPULAR
}
