-- Servicio `usuarios`: identidad y roles.
-- Ninguna otra tabla del sistema referencia estas por clave foránea; los demás
-- servicios guardan `usuario_id` como valor plano.

CREATE TABLE usuario (
    id            BIGSERIAL PRIMARY KEY,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    email_address VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    phone_number  VARCHAR(9)   NOT NULL,
    address       VARCHAR(200) NOT NULL,
    rol           VARCHAR(20)  NOT NULL DEFAULT 'CLIENTE',
    creado_en     TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_usuario_email UNIQUE (email_address),
    CONSTRAINT ck_usuario_rol CHECK (rol IN ('CLIENTE', 'EMPLEADO', 'ADMINISTRADOR'))
);

CREATE INDEX idx_usuario_email ON usuario (email_address);

CREATE TABLE empleado (
    id         BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT      NOT NULL,
    cargo      VARCHAR(50) NOT NULL DEFAULT 'SIN_ASIGNAR',
    CONSTRAINT fk_empleado_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT uk_empleado_usuario UNIQUE (usuario_id)
);
