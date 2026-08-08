-- Columna de bloqueo optimista para la saga.
--
-- La entidad SagaCheckout declara @Version pero V2 no creó la columna, así que
-- la validación de esquema fallaba al arrancar. Se corrige en una migración
-- nueva en lugar de editar V2: esa ya se aplicó y cambiarla rompería su suma
-- de verificación en flyway_schema_history.
--
-- Sin esta columna, dos hilos podrían avanzar la misma saga a la vez (por
-- ejemplo, el barrendero compensando mientras llega la confirmación del pago).

ALTER TABLE saga_checkout ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
