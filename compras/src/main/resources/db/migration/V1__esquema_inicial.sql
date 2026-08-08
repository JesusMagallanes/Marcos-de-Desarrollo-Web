-- Servicio `compras`.
--
-- Aquí es donde se cortaron las claves foráneas que cruzaban servicios:
--   carrito.usuario_id        -> usuarios   (ahora BIGINT plano)
--   carrito_item.producto_id  -> catalogo   (ahora BIGINT plano)
--   pedido.usuario_id         -> usuarios   (ahora BIGINT plano)
--   detalle_pedido.producto_id-> catalogo   (ahora BIGINT plano)
--
-- La FK envios.pedido_id sí se mantiene: ambas tablas viven en este esquema.

CREATE TABLE carrito (
    id         BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT    NOT NULL,
    creado_en  TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_carrito_usuario UNIQUE (usuario_id)
);

CREATE TABLE carrito_item (
    id          BIGSERIAL PRIMARY KEY,
    carrito_id  BIGINT  NOT NULL,
    producto_id BIGINT  NOT NULL,
    cantidad    INTEGER NOT NULL,
    CONSTRAINT fk_item_carrito FOREIGN KEY (carrito_id) REFERENCES carrito (id) ON DELETE CASCADE,
    CONSTRAINT uk_item_carrito_producto UNIQUE (carrito_id, producto_id),
    CONSTRAINT ck_item_cantidad CHECK (cantidad > 0)
);

CREATE INDEX idx_item_carrito ON carrito_item (carrito_id);

CREATE TABLE metodopago (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL,
    description VARCHAR(200) NOT NULL,
    CONSTRAINT uk_metodopago_name UNIQUE (name)
);

CREATE TABLE pedido (
    id            BIGSERIAL PRIMARY KEY,
    usuario_id    BIGINT         NOT NULL,
    metodopago_id BIGINT         NOT NULL,
    total         NUMERIC(12, 2) NOT NULL,
    estado        VARCHAR(20)    NOT NULL DEFAULT 'PENDIENTE',
    -- Referencia del pago en la pasarela; permite conciliar y evita duplicados.
    payment_id    VARCHAR(100),
    creado_en     TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_pedido_metodopago FOREIGN KEY (metodopago_id) REFERENCES metodopago (id),
    CONSTRAINT ck_pedido_estado CHECK (estado IN ('PENDIENTE', 'PAGADO', 'EN_TRANSITO', 'ENTREGADO', 'CANCELADO')),
    CONSTRAINT uk_pedido_payment UNIQUE (payment_id)
);

CREATE INDEX idx_pedido_usuario ON pedido (usuario_id);
CREATE INDEX idx_pedido_estado ON pedido (estado);

CREATE TABLE detalle_pedido (
    id              BIGSERIAL PRIMARY KEY,
    pedido_id       BIGINT         NOT NULL,
    producto_id     BIGINT         NOT NULL,
    -- Copia del nombre, imagen y precio en el momento de la compra: el pedido
    -- no debe cambiar si luego se edita o borra el producto en catalogo.
    producto_nombre VARCHAR(150)   NOT NULL,
    producto_imagen VARCHAR(1000),
    cantidad        INTEGER        NOT NULL,
    precio_unitario NUMERIC(12, 2) NOT NULL,
    total           NUMERIC(12, 2) NOT NULL,
    CONSTRAINT fk_detalle_pedido FOREIGN KEY (pedido_id) REFERENCES pedido (id) ON DELETE CASCADE,
    CONSTRAINT ck_detalle_cantidad CHECK (cantidad > 0)
);

CREATE INDEX idx_detalle_pedido ON detalle_pedido (pedido_id);

CREATE TABLE envios (
    id                      BIGSERIAL PRIMARY KEY,
    pedido_id               BIGINT       NOT NULL,
    direccion               VARCHAR(200) NOT NULL,
    estado_envio            VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
    fecha_envio_programado  TIMESTAMP,
    fecha_envio_entregado   TIMESTAMP,
    CONSTRAINT fk_envio_pedido FOREIGN KEY (pedido_id) REFERENCES pedido (id) ON DELETE CASCADE,
    CONSTRAINT uk_envio_pedido UNIQUE (pedido_id),
    CONSTRAINT ck_envio_estado CHECK (estado_envio IN ('PENDIENTE', 'EN_TRANSITO', 'ENTREGADO'))
);

-- Métodos de pago base para que el checkout funcione desde el primer arranque.
INSERT INTO metodopago (name, description) VALUES
    ('MercadoPago', 'Tarjeta de crédito, débito, Yape y Plin vía MercadoPago'),
    ('Contra entrega', 'Pago en efectivo al recibir el pedido (Lima Metropolitana)');
