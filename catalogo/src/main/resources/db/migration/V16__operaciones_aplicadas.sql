-- Idempotencia de la sincronización offline.
--
-- El cliente que trabajó sin conexión encola sus operaciones y las reenvía
-- cuando vuelve la red. Los reintentos pueden duplicar un efecto que SÍ llegó
-- (la respuesta de éxito se perdió, no la operación), así que cada operación
-- trae su propio identificador (operacion_id, un UUID del cliente) y esta
-- tabla registra las ya aplicadas.
--
-- La PRIMARY KEY sobre operacion_id ES el mecanismo anti-duplicados: la
-- inserción ocurre dentro de la MISMA transacción que el efecto, así que o
-- los dos se confirman o ninguno. Un reenvío choca con la constraint y no
-- vuelve a aplicar nada.

CREATE TABLE operaciones_aplicadas (
    id         VARCHAR(64) PRIMARY KEY,
    usuario_id BIGINT      NOT NULL,
    tipo       VARCHAR(30) NOT NULL,
    creado_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_operaciones_creado_en ON operaciones_aplicadas (creado_en);
