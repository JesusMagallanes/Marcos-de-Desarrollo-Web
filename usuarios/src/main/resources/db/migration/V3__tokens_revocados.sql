-- A07/A08: revocación de tokens.
--
-- Los JWT son autocontenidos, así que "cerrar sesión" no los invalida por sí
-- solo: siguen siendo válidos hasta que expiran. Se guarda el `jti` de los
-- tokens retirados para poder rechazarlos antes de tiempo.
--
-- Solo se almacenan los REFRESH (larga vida). Los access token duran entre 30
-- y 120 minutos y no compensa consultar la base en cada petición.

CREATE TABLE token_revocado (
    jti        VARCHAR(64) PRIMARY KEY,
    usuario_id BIGINT      NOT NULL,
    motivo     VARCHAR(30) NOT NULL,
    expira_en  TIMESTAMP   NOT NULL,
    creado_en  TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- Para purgar los que ya expiraron por su cuenta.
CREATE INDEX idx_token_revocado_expira ON token_revocado (expira_en);
CREATE INDEX idx_token_revocado_usuario ON token_revocado (usuario_id);
