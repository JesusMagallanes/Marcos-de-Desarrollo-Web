-- Recuperar la contraseña con un código enviado por correo.
--
-- Código de 6 dígitos y no enlace: la app móvil no tiene a dónde llevar un
-- enlace web, y un código se escribe igual en el teléfono que en la web.
--
-- Se guarda el HASH (BCrypt), nunca el código. Seis dígitos son un millón de
-- combinaciones: con un SHA-256 sin sal, una copia de esta tabla se revierte
-- entera en segundos. Con BCrypt no compensa, y de todas formas el código
-- caduca a los quince minutos.
--
-- `intentos` acota la fuerza bruta en línea: a los cinco fallos el código deja
-- de valer aunque no haya caducado, así que probar el millón de combinaciones
-- obligaría a pedir doscientos mil códigos, y pedirlos tiene su propio cupo.
--
-- `usado_en` hace que cada código sirva UNA vez. Pedir uno nuevo marca los
-- anteriores como usados: solo vale el último que llegó al buzón.
--
-- Solo DDL: no toca filas, así que no hace falta `SET LOCAL app.omitir_rls`
-- (ver el aviso de V7__row_level_security.sql).

CREATE TABLE codigo_recuperacion (
    id          BIGSERIAL PRIMARY KEY,
    usuario_id  BIGINT                   NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    codigo_hash VARCHAR(100)             NOT NULL,
    intentos    INTEGER                  NOT NULL DEFAULT 0,
    expira_en   TIMESTAMP WITH TIME ZONE NOT NULL,
    usado_en    TIMESTAMP WITH TIME ZONE,
    creado_en   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- El camino de siempre: el código vigente de una cuenta.
CREATE INDEX idx_codigo_recuperacion_usuario ON codigo_recuperacion (usuario_id, creado_en DESC);

-- Como `token_revocado`: todo el flujo corre como sistema (no hay usuario
-- autenticado: quien recupera la clave es justo quien no puede entrar). La
-- política existe para que nadie con sesión pueda leer los códigos de otro si
-- algún día se expone la tabla por otra vía.
ALTER TABLE usuarios.codigo_recuperacion ENABLE ROW LEVEL SECURITY;
ALTER TABLE usuarios.codigo_recuperacion FORCE ROW LEVEL SECURITY;

CREATE POLICY codigo_recuperacion_sistema ON usuarios.codigo_recuperacion
    USING (usuarios.rls_es_sistema())
    WITH CHECK (usuarios.rls_es_sistema());
