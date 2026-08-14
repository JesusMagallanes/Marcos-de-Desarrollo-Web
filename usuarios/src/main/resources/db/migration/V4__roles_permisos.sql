-- Roles y permisos: RBAC configurable desde el panel de administración.
--
-- Los roles dejan de ser un enum del código y pasan a ser datos. El panel
-- puede crear roles nuevos, clasificarlos como TRABAJADOR o CLIENTE y
-- asignarles permisos. El claim `rol` del JWT sigue siendo el nombre del rol,
-- así que el resto de servicios (catálogo, compras, gateway) no cambian de
-- contrato: siguen leyendo un string.

CREATE TABLE rol (
    nombre      VARCHAR(50)  PRIMARY KEY,
    descripcion VARCHAR(200) NOT NULL DEFAULT '',
    tipo        VARCHAR(20)  NOT NULL,
    sistema     BOOLEAN      NOT NULL DEFAULT FALSE,
    creado_en   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE rol_permiso (
    rol_nombre VARCHAR(50) NOT NULL,
    permiso    VARCHAR(40) NOT NULL,
    PRIMARY KEY (rol_nombre, permiso),
    CONSTRAINT fk_rol_permiso_rol FOREIGN KEY (rol_nombre) REFERENCES rol (nombre) ON DELETE CASCADE
);

-- El rol del usuario deja de estar limitado a los 3 valores del CHECK: ahora
-- puede referirse a cualquier rol creado en el panel.
ALTER TABLE usuario ALTER COLUMN rol TYPE VARCHAR(50);
ALTER TABLE usuario DROP CONSTRAINT ck_usuario_rol;
ALTER TABLE usuario ALTER COLUMN rol SET DEFAULT 'CLIENTE';

CREATE INDEX idx_usuario_rol ON usuario (rol);
