-- Galería de imágenes del producto.
--
-- `image_url` en `producto` se mantiene como imagen principal (la primera de
-- la lista) para tarjetas y carrito; esta tabla guarda la galería completa,
-- ordenada, sin límite fijo de imágenes.

CREATE TABLE producto_imagen (
    id          BIGSERIAL PRIMARY KEY,
    producto_id BIGINT        NOT NULL,
    url         VARCHAR(1000) NOT NULL,
    posicion    INTEGER       NOT NULL,
    CONSTRAINT fk_producto_imagen_producto FOREIGN KEY (producto_id)
        REFERENCES producto (id) ON DELETE CASCADE,
    CONSTRAINT ck_producto_imagen_posicion CHECK (posicion >= 0)
);

CREATE INDEX idx_producto_imagen_producto ON producto_imagen (producto_id);
CREATE UNIQUE INDEX uk_producto_imagen_producto_posicion ON producto_imagen (producto_id, posicion);
