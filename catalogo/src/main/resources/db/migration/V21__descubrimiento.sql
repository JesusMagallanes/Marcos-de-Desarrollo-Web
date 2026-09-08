-- Descubrimiento, fase 1: sujeto, eventos, impresiones, perfil de intereses.
--
-- Vive en el esquema `catalogo` y no en un servicio nuevo a propósito. Todas
-- las consultas de similitud leen `producto_atributo`, `categoria` y `marca`;
-- desde fuera del servicio serían llamadas HTTP por petición, y el
-- presupuesto de latencia del Home no lo aguanta. El paquete Java y estas
-- tablas quedan autocontenidos, así que extraerlo después es mover carpetas.

-- ═══════════════ 1 · Sujeto ═══════════════
--
-- La identidad del que explora, tenga cuenta o no. El sistema NO se diseña
-- alrededor de `usuario_id`: la mayor parte del tráfico de descubrimiento
-- viene de gente sin sesión iniciada, y si el perfil colgara de la cuenta se
-- perdería justo la señal que se quiere capturar.
--
-- `usuario_id` es un valor plano, sin clave foránea: el usuario vive en el
-- esquema de otro servicio y cruzar servicios con una FK es lo que este
-- proyecto evita (misma regla que `valoracion.usuario_id`).

CREATE TABLE sujeto (
    id           UUID PRIMARY KEY,
    -- NULL mientras sea anónimo. Se rellena al iniciar sesión.
    usuario_id   BIGINT,
    -- Último distrito conocido (INEI, 6 dígitos). Ver la degradación en V22.
    ubigeo       VARCHAR(6),
    creado_en    TIMESTAMPTZ NOT NULL DEFAULT now(),
    visto_en     TIMESTAMPTZ NOT NULL DEFAULT now(),
    /*
     * Si este sujeto se fusionó en otro, aquí queda a cuál.
     *
     * La fila NO se borra: hay pestañas abiertas y eventos en vuelo que
     * siguen mandando el id viejo, y borrarla los tiraría. Queda como
     * redirección y el servicio resuelve el superviviente.
     */
    fusionado_en UUID,
    CONSTRAINT fk_sujeto_fusion FOREIGN KEY (fusionado_en) REFERENCES sujeto (id),
    CONSTRAINT ck_sujeto_no_se_fusiona_consigo CHECK (fusionado_en IS NULL OR fusionado_en <> id)
);

-- Un usuario tiene UN sujeto vivo. Parcial porque los anónimos son todos NULL
-- y un índice único normal los dejaría pasar igual, pero este además es más
-- pequeño y es el que sirve para resolver el sujeto al iniciar sesión.
CREATE UNIQUE INDEX uk_sujeto_usuario ON sujeto (usuario_id)
    WHERE usuario_id IS NOT NULL AND fusionado_en IS NULL;

-- ═══════════════ 2 · Eventos ═══════════════
--
-- Log de solo-anexado. No se actualiza ni se borra fila a fila: se purga por
-- ventana (90 días) y lo que sobrevive son los agregados.
--
-- ITEM: `item_tipo` + `item_id` y NO una FK a `producto`. Hoy solo hay
-- productos, pero la unidad conceptual es el ítem, porque después habrá
-- negocios, servicios y publicaciones. Atarlo a `producto` obligaría a
-- rehacer la tabla y todo lo que cuelga de ella.

CREATE TABLE evento_interaccion (
    id           BIGSERIAL PRIMARY KEY,
    sujeto_id    UUID        NOT NULL,
    sesion_id    UUID,
    tipo         VARCHAR(24) NOT NULL,
    item_tipo    VARCHAR(16),
    item_id      BIGINT,
    categoria_id BIGINT,
    ubigeo       VARCHAR(6),
    -- Milisegundos de permanencia. El techo se aplica al puntuar, no aquí:
    -- el dato crudo se guarda como vino para poder recalibrar después.
    dwell_ms     INTEGER,
    -- De qué módulo salió lo que se tocó. Sin esto no se puede medir qué
    -- carrusel funciona ni corregir el sesgo de posición al entrenar.
    origen       VARCHAR(32),
    posicion     SMALLINT,
    metadata     JSONB,
    ocurrido_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_evento_sujeto FOREIGN KEY (sujeto_id)
        REFERENCES sujeto (id) ON DELETE CASCADE,
    CONSTRAINT fk_evento_categoria FOREIGN KEY (categoria_id)
        REFERENCES categoria (id) ON DELETE SET NULL,
    CONSTRAINT ck_evento_dwell CHECK (dwell_ms IS NULL OR dwell_ms >= 0),
    -- O van los dos o no va ninguno: un item_id sin tipo no se puede resolver.
    CONSTRAINT ck_evento_item CHECK ((item_tipo IS NULL) = (item_id IS NULL))
);

-- El historial reciente de un sujeto, que es la consulta caliente.
CREATE INDEX idx_evento_sujeto ON evento_interaccion (sujeto_id, ocurrido_en DESC);
-- El rollup de tendencias y la purga por retención barren por fecha.
CREATE INDEX idx_evento_fecha ON evento_interaccion (ocurrido_en);
-- Estadísticas por ítem (co-visita en fase 2, «visto recientemente» ya).
CREATE INDEX idx_evento_item ON evento_interaccion (item_tipo, item_id, ocurrido_en DESC)
    WHERE item_id IS NOT NULL;

COMMENT ON TABLE evento_interaccion IS
    'Solo-anexado. Retención 90 dias; despues solo quedan agregados.';

-- Sin particionar todavía, y es una decisión: particionar por mes exige un
-- trabajo que cree particiones y complica cada migración. Con el índice por
-- fecha, la purga por ventana rinde de sobra al volumen actual. Cuando el
-- barrido empiece a notarse, se convierte en particionada por `ocurrido_en`.

-- ═══════════════ 3 · Impresiones ═══════════════
--
-- Tabla aparte y no un tipo más de evento. Por volumen: un Home pinta ~60
-- ítems y se hace clic en uno. Mezclarlas multiplicaría por sesenta la tabla
-- de eventos y arruinaría todas sus consultas.
--
-- Sirven para dos cosas distintas: saber qué recomendaciones no funcionan
-- (CTR por módulo) y dejar de enseñar lo que ya se ignoró tres veces.

CREATE TABLE impresion (
    id          BIGSERIAL PRIMARY KEY,
    sujeto_id   UUID        NOT NULL,
    item_tipo   VARCHAR(16) NOT NULL,
    item_id     BIGINT      NOT NULL,
    modulo      VARCHAR(40) NOT NULL,
    posicion    SMALLINT,
    con_clic    BOOLEAN     NOT NULL DEFAULT FALSE,
    mostrado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_impresion_sujeto FOREIGN KEY (sujeto_id)
        REFERENCES sujeto (id) ON DELETE CASCADE
);

-- El tope de fatiga pregunta «cuántas veces le enseñé esto sin que lo tocara».
CREATE INDEX idx_impresion_fatiga
    ON impresion (sujeto_id, item_tipo, item_id, mostrado_en DESC);
-- El CTR por módulo, que es la métrica de si un carrusel se gana su sitio.
CREATE INDEX idx_impresion_modulo ON impresion (modulo, mostrado_en);

-- ═══════════════ 4 · Descartes ═══════════════
--
-- «No me interesa» y «ocultar». Tabla propia y no una consulta sobre eventos
-- porque esto se lee en CADA recomendación para filtrar duro, y escanear el
-- log de eventos para eso sería el cuello de botella del Home.

CREATE TABLE item_descartado (
    sujeto_id UUID        NOT NULL,
    item_tipo VARCHAR(16) NOT NULL,
    item_id   BIGINT      NOT NULL,
    motivo    VARCHAR(24) NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (sujeto_id, item_tipo, item_id),
    CONSTRAINT fk_descartado_sujeto FOREIGN KEY (sujeto_id)
        REFERENCES sujeto (id) ON DELETE CASCADE
);

-- ═══════════════ 5 · Perfil de intereses ═══════════════
--
-- UNA tabla, no tres.
--
-- Se propusieron `perfil_interes`, `interes_categoria` e `interes_atributo`.
-- Las tres tienen exactamente la misma forma —sujeto, algo, un score— y
-- separarlas obligaría a consultar tres sitios y a repetir la lógica de
-- decaimiento en cada uno. `tipo_faceta` discrimina y ya.
--
-- Las facetas de tipo `atributo` salen de `producto_atributo`: no se guarda
-- aquí ninguna característica que ya esté representada allí, solo cuánto le
-- interesa al sujeto.

CREATE TABLE perfil_faceta (
    sujeto_id      UUID          NOT NULL,
    -- categoria | marca | atributo | precio
    tipo_faceta    VARCHAR(16)   NOT NULL,
    -- 'laptops' | 'ASUS' | 'pulgadas=27' | '1500-3000'
    faceta         VARCHAR(120)  NOT NULL,
    score          NUMERIC(14,4) NOT NULL DEFAULT 0,
    -- Cuántos eventos lo sostienen. Alimenta la confianza, que evita que un
    -- único evento convierta una casualidad en el interés dominante.
    eventos        INTEGER       NOT NULL DEFAULT 0,
    /*
     * No es auditoría: es el parámetro del olvido.
     *
     * El decaimiento se aplica al ESCRIBIR, no al leer, usando el tiempo
     * transcurrido desde aquí:
     *
     *     score = score * 2^(-Δt/vida_media) + peso_del_evento
     *
     * Así el perfil se mantiene al día con un UPDATE por evento, sin trabajo
     * nocturno y sin recorrer nunca el historial.
     */
    actualizado_en TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (sujeto_id, tipo_faceta, faceta),
    CONSTRAINT fk_perfil_sujeto FOREIGN KEY (sujeto_id)
        REFERENCES sujeto (id) ON DELETE CASCADE
);

-- «Dame las N facetas más fuertes de este sujeto», que es de lo que sale toda
-- recomendación personal.
CREATE INDEX idx_perfil_top ON perfil_faceta (sujeto_id, tipo_faceta, score DESC);

-- ═══════════════ 6 · Tendencias precalculadas ═══════════════
--
-- Calcular tendencias sobre el log de eventos en cada petición no escala. Se
-- consolidan por zona cada hora y el Home solo lee.
--
-- `sujetos` es el piso de privacidad: una fila con menos de N sujetos
-- distintos no se sirve, porque con cuatro personas en un distrito la
-- «tendencia» delata a quien la generó.

CREATE TABLE tendencia_item (
    -- DISTRITO | PROVINCIA | DEPARTAMENTO | NACIONAL
    nivel        VARCHAR(12)   NOT NULL,
    -- Prefijo del ubigeo INEI según el nivel; '' en nacional.
    zona         VARCHAR(6)    NOT NULL,
    item_tipo    VARCHAR(16)   NOT NULL,
    item_id      BIGINT        NOT NULL,
    categoria_id BIGINT,
    score        NUMERIC(12,4) NOT NULL,
    sujetos      INTEGER       NOT NULL,
    calculado_en TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (nivel, zona, item_tipo, item_id)
);

CREATE INDEX idx_tendencia_orden ON tendencia_item (nivel, zona, score DESC);

COMMENT ON COLUMN tendencia_item.score IS
    'Velocidad reciente ponderada por crecimiento, no visitas acumuladas: un '
    'producto con 80 vistas esta semana y 10 la anterior va por delante de uno '
    'con 1000 historicas y 20 esta semana.';
