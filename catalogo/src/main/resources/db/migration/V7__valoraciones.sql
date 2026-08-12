-- Valoraciones de clientes sobre productos.
--
-- La identidad del cliente (usuario_id) viene del JWT y nunca se acepta por
-- URL ni por cuerpo: la restricción única garantiza una sola valoración por
-- cliente y producto, y re-valorar actualiza la existente en lugar de duplicar.
-- `nombre` es el nombre mostrado que envía el cliente (decorativo).

CREATE TABLE valoracion (
    id             BIGSERIAL PRIMARY KEY,
    producto_id    BIGINT NOT NULL REFERENCES producto (id) ON DELETE CASCADE,
    usuario_id     BIGINT NOT NULL,
    nombre         VARCHAR(120) NOT NULL,
    calificacion   SMALLINT NOT NULL CHECK (calificacion BETWEEN 1 AND 5),
    comentario     TEXT NOT NULL,
    creado_en      TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_valoracion_producto_usuario UNIQUE (producto_id, usuario_id)
);

CREATE INDEX idx_valoracion_producto ON valoracion (producto_id);
