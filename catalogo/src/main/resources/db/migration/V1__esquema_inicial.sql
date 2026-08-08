-- Servicio `catalogo`. Las tres tablas se referencian entre sí por FK reales
-- porque viven en el mismo esquema; nada aquí apunta fuera del servicio.

CREATE TABLE categoria (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100)  NOT NULL,
    slug        VARCHAR(120)  NOT NULL,
    description VARCHAR(500)  NOT NULL,
    url_image   VARCHAR(1000),
    CONSTRAINT uk_categoria_name UNIQUE (name),
    CONSTRAINT uk_categoria_slug UNIQUE (slug)
);

CREATE TABLE marca (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(100)  NOT NULL,
    descripcion  VARCHAR(1000) NOT NULL,
    categoria_id BIGINT        NOT NULL,
    CONSTRAINT uk_marca_name UNIQUE (name),
    CONSTRAINT fk_marca_categoria FOREIGN KEY (categoria_id) REFERENCES categoria (id)
);

CREATE INDEX idx_marca_categoria ON marca (categoria_id);

CREATE TABLE producto (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(150)   NOT NULL,
    description  VARCHAR(500)   NOT NULL,
    precio       NUMERIC(12, 2) NOT NULL,
    image_url    VARCHAR(1000),
    stock        INTEGER        NOT NULL DEFAULT 0,
    categoria_id BIGINT         NOT NULL,
    marca_id     BIGINT,
    -- Bloqueo optimista para el descuento de stock concurrente.
    version      BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_producto_precio CHECK (precio >= 0),
    CONSTRAINT ck_producto_stock CHECK (stock >= 0),
    CONSTRAINT fk_producto_categoria FOREIGN KEY (categoria_id) REFERENCES categoria (id),
    CONSTRAINT fk_producto_marca FOREIGN KEY (marca_id) REFERENCES marca (id)
);

CREATE INDEX idx_producto_categoria ON producto (categoria_id);
CREATE INDEX idx_producto_marca ON producto (marca_id);
