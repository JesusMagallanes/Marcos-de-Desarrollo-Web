-- Descubrimiento, fase 3: poder responder si el recomendador funciona.
--
-- Hasta aqui el sistema decide y no deja constancia de POR QUE decidio. La
-- tabla `impresion` sabe que se enseño, en que modulo y en que posicion, y eso
-- basta para la fatiga pero no para evaluar: el modulo no es la razon —el
-- carrusel colaborativo mezcla CO_VIEWED y SIMILAR_SUBJECT en uno solo— y no
-- hay ni score ni forma de saber con que configuracion de pesos se genero.
--
-- Sin eso, ajustar un peso es una corazonada. Con esto se puede comparar.

-- ─────────────────────────────────────────────────────────────────────────────
-- Lo que el sistema DECIDIO servir
-- ─────────────────────────────────────────────────────────────────────────────
/*
 * Va aparte de `impresion` a proposito, y la diferencia es la que hace que las
 * dos sirvan.
 *
 * `impresion` es lo que el usuario LLEGO A VER: la escribe el navegador cuando
 * una tarjeta entra en pantalla, y de ahi sale la fatiga. Si aqui se mezclara
 * lo servido con lo visto, la fatiga empezaria a castigar productos que el
 * usuario nunca llego a desplazar hasta ellos, que es lo contrario de lo que
 * significa «ya te lo enseñe y lo ignoraste».
 *
 * Esta tabla es lo que el BACKEND decidio, se viera o no. Es la unica forma de
 * medir el hueco entre lo que el sistema propone y lo que la gente alcanza a
 * mirar, que en un Home largo es enorme.
 */
CREATE TABLE recomendacion_servida (
    id             BIGSERIAL PRIMARY KEY,
    sujeto_id      UUID          NOT NULL,
    item_tipo      VARCHAR(16)   NOT NULL,
    item_id        BIGINT        NOT NULL,

    -- El carrusel donde salio. Es lo que ve el usuario.
    modulo         VARCHAR(40)   NOT NULL,

    /*
     * El generador que lo propuso. NO es derivable del modulo: el carrusel
     * colaborativo mezcla dos razones distintas, y saber cual de las dos acierta
     * es justo lo que se quiere medir.
     */
    razon          VARCHAR(24)   NOT NULL,

    -- Empieza en 0. Se guarda porque la posicion sesga el clic muchisimo mas
    -- que la calidad, y comparar la 1 con la 10 sin tenerla en cuenta es
    -- medirse el sesgo a uno mismo.
    posicion       SMALLINT      NOT NULL,

    -- El score final tras normalizar, ponderar y castigar. Permite ver si el
    -- orden que produjo el ranker se corresponde con lo que la gente eligio.
    score          NUMERIC(10,6) NOT NULL,

    /*
     * Que configuracion de pesos produjo esto.
     *
     * Lleva una huella de los pesos efectivos, no solo una etiqueta a mano: una
     * etiqueta se olvida de subir y entonces dos configuraciones distintas
     * quedan registradas con el mismo nombre, que es peor que no versionar,
     * porque la comparacion parece valida y no lo es.
     */
    ranker_version VARCHAR(24)   NOT NULL,

    -- Si habia perfil del que tirar. Separa el arranque en frio, que se mide
    -- aparte porque su rendimiento no es comparable con el resto.
    con_perfil     BOOLEAN       NOT NULL,

    servido_en     TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT fk_servida_sujeto FOREIGN KEY (sujeto_id)
        REFERENCES sujeto (id) ON DELETE CASCADE,
    CONSTRAINT ck_servida_posicion CHECK (posicion >= 0)
);

-- El cruce de la agregacion: por dia, y dentro del dia por modulo y razon.
CREATE INDEX idx_servida_rollup ON recomendacion_servida (servido_en, modulo, razon);

-- Casar una accion posterior con lo que se le sirvio a ese sujeto.
CREATE INDEX idx_servida_atribucion
    ON recomendacion_servida (sujeto_id, item_id, servido_en DESC);

-- ─────────────────────────────────────────────────────────────────────────────
-- Las cuentas ya hechas
-- ─────────────────────────────────────────────────────────────────────────────
/*
 * Agregado por dia y no fila a fila.
 *
 * Un panel que recorriera millones de impresiones en cada consulta acabaria
 * costando mas que servir la tienda. Aqui hay una fila por combinacion de dia,
 * modulo, razon, version y banda de posicion: unas decenas al dia, que se
 * pueden conservar durante anios sin que importe.
 *
 * Es ademas lo que hace la medicion REPRODUCIBLE: el detalle se puede purgar
 * —y se purga— mientras el agregado sobrevive.
 */
CREATE TABLE metrica_descubrimiento (
    dia            DATE          NOT NULL,
    modulo         VARCHAR(40)   NOT NULL,
    razon          VARCHAR(24)   NOT NULL,
    ranker_version VARCHAR(24)   NOT NULL,

    /*
     * Banda de posicion en vez de posicion exacta.
     *
     * La diferencia entre la primera y la segunda tarjeta importa; entre la
     * novena y la decima, no. Agrupar en bandas evita multiplicar filas por
     * cada posicion y sigue permitiendo ver la caida por posicion, que es lo
     * unico que se busca aqui.
     */
    banda_posicion SMALLINT      NOT NULL,

    -- Con perfil o arranque en frio. No se mezclan: sus tasas no son comparables.
    con_perfil     BOOLEAN       NOT NULL,

    servidas       BIGINT        NOT NULL DEFAULT 0,
    vistas         BIGINT        NOT NULL DEFAULT 0,
    clics          BIGINT        NOT NULL DEFAULT 0,

    -- Acciones posteriores atribuidas. Un clic no es un exito; una compra si.
    vistas_profundas BIGINT      NOT NULL DEFAULT 0,
    carritos       BIGINT        NOT NULL DEFAULT 0,
    compras        BIGINT        NOT NULL DEFAULT 0,

    -- Sujetos distintos alcanzados. Es el piso de privacidad de este agregado:
    -- una fila sostenida por una sola persona no describe un patron.
    sujetos        BIGINT        NOT NULL DEFAULT 0,

    calculado_en   TIMESTAMPTZ   NOT NULL DEFAULT now(),

    PRIMARY KEY (dia, modulo, razon, ranker_version, banda_posicion, con_perfil)
);

CREATE INDEX idx_metrica_dia ON metrica_descubrimiento (dia DESC);

/*
 * RETENCION
 *
 * `recomendacion_servida` e `impresion` son detalle y crecen con el trafico:
 * decenas de filas por visita. Se conservan los dias que diga
 * `descubrimiento.retencion-dias` (90 por defecto, suficiente para evaluar un
 * trimestre) y despues se borran. Lo hace `RetencionService`.
 *
 * `metrica_descubrimiento` NO se purga: son unas decenas de filas al dia y es
 * lo que permite comparar el recomendador de hoy con el de hace un ano.
 *
 * Ninguna de las tres guarda nada personal: solo el identificador opaco de
 * sujeto, que desaparece en cascada si el sujeto se borra.
 */
COMMENT ON TABLE recomendacion_servida IS
    'Detalle de lo servido. Se purga segun descubrimiento.retencion-dias.';
COMMENT ON TABLE metrica_descubrimiento IS
    'Agregado diario. No se purga: es la memoria de como evoluciona el sistema.';
