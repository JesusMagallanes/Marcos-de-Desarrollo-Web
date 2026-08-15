-- Rol COLABORADOR y solicitudes para obtenerlo.
--
-- Un cliente que quiere vender rellena una solicitud; el administrador la
-- aprueba o la rechaza. Al aprobarla, el usuario pasa a COLABORADOR y puede
-- publicar productos (que a su vez pasan por moderación, en el esquema
-- `catalogo`).
--
-- Ver docs/contrato-colaboradores.md para la forma exacta de la API.

-- El rol COLABORADOR no se crea aquí.
--
-- La V4 convirtió los roles en datos: dejaron de ser una lista cerrada en la
-- base para poder crearse y editarse desde el panel. Las filas de `rol` las
-- siembra `RolSeeder` al arrancar, de forma idempotente, y ahí es donde se
-- añadió COLABORADOR junto a los otros tres.
--
-- Se deja en un solo sitio a propósito: tenerlo también aquí como INSERT
-- significaría dos definiciones del mismo rol (descripción, tipo, permisos) que
-- se irían separando en cuanto alguien cambiase una y olvidase la otra.

CREATE TABLE solicitud_colaborador (
    id                BIGSERIAL PRIMARY KEY,
    usuario_id        BIGINT       NOT NULL,

    -- Datos del negocio, tal y como los escribe el solicitante.
    nombre_comercial  VARCHAR(120) NOT NULL,
    -- DNI (8) o RUC (11). Se guarda como texto: son identificadores, no
    -- números, y un RUC con ceros por delante perdería los ceros.
    documento         VARCHAR(11)  NOT NULL,
    telefono_contacto VARCHAR(9)   NOT NULL,
    direccion         VARCHAR(200) NOT NULL,
    rubro             VARCHAR(120) NOT NULL,
    descripcion       TEXT         NOT NULL,

    estado            VARCHAR(12)  NOT NULL DEFAULT 'PENDIENTE',
    -- Obligatorio al rechazar; el solicitante lo ve para saber qué corregir.
    motivo_rechazo    VARCHAR(500),
    -- Quién resolvió. Sin FK a usuario a propósito: si algún día se borra la
    -- cuenta del administrador, la traza de quién aprobó no debe desaparecer.
    resuelta_por      BIGINT,

    creada_en         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resuelta_en       TIMESTAMPTZ,

    CONSTRAINT fk_solicitud_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuario (id) ON DELETE CASCADE,

    CONSTRAINT ck_solicitud_estado
        CHECK (estado IN ('PENDIENTE', 'APROBADA', 'RECHAZADA')),

    -- Coherencia entre estado y sus campos: una rechazada tiene motivo y una
    -- pendiente no puede estar resuelta. Se comprueba en la base y no solo en
    -- Java porque el estado inconsistente es el que luego nadie sabe explicar.
    CONSTRAINT ck_solicitud_motivo CHECK (
        (estado = 'RECHAZADA' AND motivo_rechazo IS NOT NULL)
        OR (estado <> 'RECHAZADA' AND motivo_rechazo IS NULL)
    ),
    CONSTRAINT ck_solicitud_resuelta CHECK (
        (estado = 'PENDIENTE' AND resuelta_en IS NULL AND resuelta_por IS NULL)
        OR (estado <> 'PENDIENTE' AND resuelta_en IS NOT NULL)
    )
);

-- Una sola solicitud PENDIENTE por usuario.
--
-- Índice único parcial en vez de un UNIQUE normal: quien fue rechazado tiene
-- que poder volver a intentarlo, así que solo se restringe el estado abierto.
-- Puesto en la base y no solo en el servicio porque dos peticiones a la vez
-- pasarían las dos la comprobación previa y crearían dos pendientes.
CREATE UNIQUE INDEX uk_solicitud_pendiente_por_usuario
    ON solicitud_colaborador (usuario_id)
    WHERE estado = 'PENDIENTE';

-- La bandeja del administrador filtra por estado y ordena por fecha.
CREATE INDEX idx_solicitud_estado ON solicitud_colaborador (estado, creada_en DESC);

-- "Mi solicitud" busca la última del usuario.
CREATE INDEX idx_solicitud_usuario ON solicitud_colaborador (usuario_id, creada_en DESC);
