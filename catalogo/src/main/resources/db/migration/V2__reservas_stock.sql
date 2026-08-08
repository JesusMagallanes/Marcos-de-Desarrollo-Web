-- Reservas de stock: la pieza que hace posible compensar la saga de compra.
--
-- El problema: entre "el usuario pulsa pagar" y "MercadoPago confirma" pasan
-- minutos. Descontar el stock al final permite vender dos veces la última
-- unidad; descontarlo al principio lo bloquea para siempre si el usuario
-- abandona el pago.
--
-- La reserva resuelve ambos: aparta el stock con una fecha de caducidad. Si el
-- pago llega, se confirma; si no, un proceso la libera.

CREATE TABLE reserva_stock (
    id           BIGSERIAL PRIMARY KEY,
    -- Identificador de la saga que la solicitó. Permite confirmar o liberar
    -- todas las líneas de una compra de golpe, y es la clave de idempotencia.
    referencia   VARCHAR(80)  NOT NULL,
    producto_id  BIGINT       NOT NULL,
    cantidad     INTEGER      NOT NULL,
    estado       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVA',
    expira_en    TIMESTAMP    NOT NULL,
    creado_en    TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_reserva_producto FOREIGN KEY (producto_id) REFERENCES producto (id),
    CONSTRAINT ck_reserva_cantidad CHECK (cantidad > 0),
    CONSTRAINT ck_reserva_estado CHECK (estado IN ('ACTIVA', 'CONFIRMADA', 'LIBERADA', 'EXPIRADA'))
);

-- Una referencia solo puede reservar una vez cada producto: hace la operación
-- idempotente frente a reintentos de la saga.
CREATE UNIQUE INDEX uk_reserva_referencia_producto ON reserva_stock (referencia, producto_id);
CREATE INDEX idx_reserva_estado_expira ON reserva_stock (estado, expira_en);
CREATE INDEX idx_reserva_referencia ON reserva_stock (referencia);
