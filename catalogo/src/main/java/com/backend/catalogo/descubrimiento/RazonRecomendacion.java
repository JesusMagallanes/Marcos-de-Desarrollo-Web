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
    POPULAR,

    /**
     * Recién llegado al catálogo, sin historial que lo sostenga.
     *
     * <p>Hacía falta una razón propia. Un producto nuevo no llega por parecido,
     * ni por conducta ajena, ni por ser popular —no puede serlo—: llega porque
     * el sistema le reserva una oportunidad a propósito. Anotarlo como
     * {@code POPULAR} habría hecho imposible medir si esa oportunidad sirve de
     * algo, que es justo lo que hay que poder responder antes de ampliarla.
     */
    NEW_ARRIVAL,

    /**
     * Encaja con lo que esta persona está explorando en esta visita.
     *
     * <p>Distinta de {@code PERSONAL_INTEREST} a propósito, aunque las dos sean
     * «personales». Aquella sale del perfil, que describe un gusto construido
     * con meses; esta sale de la sesión en curso, que puede contradecirlo. Sin
     * separarlas no se podría responder la pregunta que importa: ¿acierta más el
     * sistema escuchando lo que alguien es o lo que alguien está haciendo ahora?
     */
    SESSION_INTENT
}
