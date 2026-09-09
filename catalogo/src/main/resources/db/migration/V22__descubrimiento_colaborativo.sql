-- Descubrimiento, fase 2: aprender de lo que hace MUCHA gente, no solo cada uno.
--
-- La fase 1 solo sabe mirar hacia dentro: de tu historial saca tus categorias,
-- tus marcas y tus atributos, y te ofrece mas de lo mismo. Eso acierta y aburre.
-- Lo que no puede deducir jamas es que quien compra ESE monitor acaba
-- necesitando ESE brazo articulado, porque esa relacion no esta en la ficha de
-- ninguno de los dos: esta en la conducta de la gente.
--
-- Estas dos tablas son derivadas. No guardan ningun hecho nuevo: son el
-- resultado de agregar `evento_interaccion` y `perfil_faceta`, y se pueden
-- borrar enteras y reconstruir en la siguiente pasada del proceso por lotes.
-- Existen por una sola razon, que es que el Home no puede pagar esa agregacion
-- en cada peticion.

-- ─────────────────────────────────────────────────────────────────────────────
-- Producto ↔ producto, por co-interaccion
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE item_relacion (
    -- Se deja el tipo aunque hoy solo haya productos: `evento_interaccion` ya
    -- admite otros (una guia, una marca) y la relacion se calcula igual.
    item_tipo    VARCHAR(16)   NOT NULL,
    item_a       BIGINT        NOT NULL,
    item_b       BIGINT        NOT NULL,

    /*
     * Coseno sobre los conjuntos de sujetos: |A∩B| / sqrt(|A|·|B|).
     *
     * El denominador es lo importante, y no es un adorno matematico. Con el
     * recuento crudo de co-visitas, el producto mas visto de la tienda seria
     * "parecido" a absolutamente todo, porque coincide con todo el mundo en
     * algo; y al recomendarlo mas, se veria mas, y subiria mas. Dividir por la
     * raiz de las dos popularidades pregunta otra cosa: no cuanta gente los vio
     * juntos, sino que PROPORCION de quienes vieron uno vieron tambien el otro.
     * Un accesorio de nicho que compra el 80 % de quienes compran cierta
     * impresora gana a un superventas que comparte publico con todo.
     */
    score        NUMERIC(10,6) NOT NULL,

    -- Cuantos SUJETOS distintos sostienen la relacion, no cuantos clics. Ver la
    -- deduplicacion en `ItemRelacionRepository`.
    soporte      INTEGER       NOT NULL,

    -- Con que ventana se calculo. Queda escrito para poder cambiarla y saber
    -- despues con que criterio se genero cada fila.
    ventana_dias SMALLINT      NOT NULL,
    calculado_en TIMESTAMPTZ   NOT NULL DEFAULT now(),

    /*
     * Cada par se guarda en LAS DOS direcciones (A→B y B→A).
     *
     * Duplica las filas, y aun asi sale a cuenta: leer "que se parece a A" es
     * entonces un recorrido de indice por `item_a`, sin OR ni UNION en la
     * consulta caliente del Home. La tabla es derivada y se reconstruye entera
     * cada hora, de modo que el coste de espacio no acumula deuda.
     */
    PRIMARY KEY (item_tipo, item_a, item_b),

    CONSTRAINT ck_item_relacion_distintos CHECK (item_a <> item_b),
    CONSTRAINT ck_item_relacion_score CHECK (score > 0 AND score <= 1),
    CONSTRAINT ck_item_relacion_soporte CHECK (soporte > 0),
    CONSTRAINT ck_item_relacion_ventana CHECK (ventana_dias > 0)
);

-- La consulta del Home: los N mas parecidos a lo que esta persona acaba de ver.
CREATE INDEX idx_item_relacion_desde ON item_relacion (item_tipo, item_a, score DESC);

-- Para barrer lo que dejo de recalcularse.
CREATE INDEX idx_item_relacion_calculo ON item_relacion (calculado_en);

-- ─────────────────────────────────────────────────────────────────────────────
-- Sujeto ↔ sujeto, por parecido de perfil
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE sujeto_similitud (
    sujeto_a     UUID          NOT NULL,
    sujeto_b     UUID          NOT NULL,

    /*
     * Coseno entre los dos vectores de `perfil_faceta`.
     *
     * El perfil ya es un vector ponderado —categorias, marcas y atributos con
     * su score— asi que no hace falta construir ninguna representacion nueva:
     * la fase 1 ya la calculo. El coseno mide el ANGULO, no la magnitud, y esa
     * es la propiedad que interesa: alguien que lleva dos anios en la tienda y
     * alguien que llego el martes pueden tener exactamente el mismo gusto con
     * scores de escala muy distinta.
     */
    score        NUMERIC(10,6) NOT NULL,

    -- Cuantas facetas comparten. Una sola coincidencia no es un parecido.
    soporte      INTEGER       NOT NULL,
    ventana_dias SMALLINT      NOT NULL,
    calculado_en TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- Igual que arriba: las dos direcciones, para que "mis vecinos" sea un
    -- recorrido de indice.
    PRIMARY KEY (sujeto_a, sujeto_b),

    /*
     * ON DELETE CASCADE en los dos lados.
     *
     * Es la mitad tecnica del derecho al olvido: al borrar un sujeto
     * desaparecen tambien las aristas que lo mencionan, sin que nadie tenga que
     * acordarse de limpiarlas. Esta tabla es lo mas cerca que el sistema esta de
     * relacionar dos personas, y no puede sobrevivir a ninguna de las dos.
     */
    CONSTRAINT fk_similitud_a FOREIGN KEY (sujeto_a) REFERENCES sujeto (id) ON DELETE CASCADE,
    CONSTRAINT fk_similitud_b FOREIGN KEY (sujeto_b) REFERENCES sujeto (id) ON DELETE CASCADE,

    CONSTRAINT ck_similitud_distintos CHECK (sujeto_a <> sujeto_b),
    CONSTRAINT ck_similitud_score CHECK (score > 0 AND score <= 1),
    CONSTRAINT ck_similitud_soporte CHECK (soporte > 0),
    CONSTRAINT ck_similitud_ventana CHECK (ventana_dias > 0)
);

CREATE INDEX idx_similitud_vecinos ON sujeto_similitud (sujeto_a, score DESC);
CREATE INDEX idx_similitud_calculo ON sujeto_similitud (calculado_en);

/*
 * El indice que necesita el proceso por lotes.
 *
 * Recorre `evento_interaccion` filtrando por fecha Y por tipo, y de ahi saca
 * pares (sujeto, item). `idx_evento_fecha` sirve para la fecha pero obliga a
 * releer y descartar los eventos que no cuentan —las busquedas, los filtros,
 * los descartes—, que son mayoria. Este lo resuelve dentro del indice.
 *
 * Parcial a proposito: los eventos sin item no participan en nada colaborativo,
 * y son casi la mitad de la tabla.
 */
CREATE INDEX idx_evento_colaborativo
    ON evento_interaccion (ocurrido_en, tipo, item_id, sujeto_id)
 WHERE item_id IS NOT NULL;
