-- Saga de compra orquestada.
--
-- El problema que resuelve: el checkout toca dos servicios y una pasarela
-- externa, y ocurre en DOS peticiones HTTP separadas por el tiempo que el
-- usuario tarde en pagar. Una transacción local no puede abarcar eso.
--
-- Antes: se cobraba, y si el descuento de stock fallaba se hacía rollback de
-- lo local... con el dinero ya cobrado y sin rastro de la inconsistencia.
--
-- Ahora el estado vive en esta tabla: si el proceso se cae a mitad, al
-- reiniciar se sabe exactamente en qué paso quedó y si toca avanzar o
-- compensar.

-- Enlace del pedido con la saga que lo creó.
ALTER TABLE pedido ADD COLUMN referencia_saga VARCHAR(80);
CREATE INDEX idx_pedido_referencia_saga ON pedido (referencia_saga);

CREATE TABLE saga_checkout (
    id             BIGSERIAL PRIMARY KEY,
    -- Referencia que viaja a MercadoPago y a las reservas de catálogo.
    referencia     VARCHAR(80)  NOT NULL,
    usuario_id     BIGINT       NOT NULL,
    metodopago_id  BIGINT       NOT NULL,
    pedido_id      BIGINT,
    payment_id     VARCHAR(100),
    total          NUMERIC(12, 2) NOT NULL,
    estado         VARCHAR(30)  NOT NULL,
    -- Últim paso completado con éxito; el orquestador reanuda desde aquí.
    paso           VARCHAR(30)  NOT NULL,
    intentos       INTEGER      NOT NULL DEFAULT 0,
    ultimo_error   VARCHAR(500),
    creado_en      TIMESTAMP    NOT NULL DEFAULT NOW(),
    actualizado_en TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_saga_referencia UNIQUE (referencia),
    CONSTRAINT fk_saga_pedido FOREIGN KEY (pedido_id) REFERENCES pedido (id) ON DELETE SET NULL,
    CONSTRAINT ck_saga_estado CHECK (estado IN
        ('INICIADA', 'ESPERANDO_PAGO', 'COMPLETADA', 'COMPENSANDO', 'COMPENSADA', 'FALLIDA')),
    CONSTRAINT ck_saga_paso CHECK (paso IN
        ('INICIO', 'STOCK_RESERVADO', 'PEDIDO_CREADO', 'PREFERENCIA_CREADA',
         'PAGO_VERIFICADO', 'STOCK_CONFIRMADO', 'PEDIDO_PAGADO', 'ENVIO_CREADO', 'FIN'))
);

CREATE INDEX idx_saga_estado ON saga_checkout (estado);
CREATE INDEX idx_saga_usuario ON saga_checkout (usuario_id);
CREATE INDEX idx_saga_payment ON saga_checkout (payment_id);
-- Para el barrido de sagas abandonadas.
CREATE INDEX idx_saga_estado_actualizado ON saga_checkout (estado, actualizado_en);

-- Idempotencia de las peticiones mutantes (A04/A08): una misma clave devuelve
-- el mismo resultado en vez de crear un segundo pedido.
CREATE TABLE clave_idempotencia (
    clave      VARCHAR(120) PRIMARY KEY,
    usuario_id BIGINT       NOT NULL,
    recurso    VARCHAR(60)  NOT NULL,
    respuesta  TEXT,
    creado_en  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_idempotencia_creado ON clave_idempotencia (creado_en);
