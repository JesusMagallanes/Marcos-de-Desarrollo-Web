-- SZ-B08: productos de colaborador, con dueño y moderación.
--
-- Hasta ahora todo producto era de la tienda y se publicaba en cuanto se
-- guardaba. Con los colaboradores (ver docs/contrato-colaboradores.md) entra
-- gente de fuera a publicar, y eso obliga a responder dos preguntas que antes
-- no existían: de quién es cada producto, y si se puede enseñar ya.
--
-- Las dos son columnas, no tablas aparte: son atributos del producto, y sacarlas
-- fuera solo añadiría un JOIN a la consulta más caliente de la tienda.

-- ── 1 · De quién es ────────────────────────────────────────────────────────

-- NULL = de la tienda. Es lo que son todos los que ya existen, y por eso la
-- columna admite nulos en vez de inventarles un dueño: un id falso apuntando a
-- un usuario que no publicó nada sería peor que la ausencia de dato.
--
-- Sin clave foránea a propósito: el usuario vive en el esquema `usuarios`, que
-- es de otro servicio. Cruzar servicios con una FK es justo lo que este proyecto
-- evita (ver "Las 5 FK que cruzaban servicios" en el README).
ALTER TABLE producto ADD COLUMN propietario_id BIGINT;

COMMENT ON COLUMN producto.propietario_id IS
    'Colaborador que lo publicó. NULL = producto de la tienda.';

-- ── 2 · Si se puede enseñar ────────────────────────────────────────────────

-- Los que ya existen son de la tienda y están publicados: APROBADO.
ALTER TABLE producto ADD COLUMN estado_moderacion VARCHAR(12) NOT NULL DEFAULT 'APROBADO';

ALTER TABLE producto ADD COLUMN motivo_rechazo VARCHAR(500);
ALTER TABLE producto ADD COLUMN moderado_por BIGINT;
ALTER TABLE producto ADD COLUMN moderado_en TIMESTAMPTZ;

ALTER TABLE producto ADD CONSTRAINT ck_producto_moderacion
    CHECK (estado_moderacion IN ('PENDIENTE', 'APROBADO', 'RECHAZADO'));

-- Coherencia entre el estado y sus campos, igual que en las solicitudes: un
-- rechazado tiene motivo y un pendiente no puede estar moderado. Se comprueba en
-- la base porque un estado incoherente es de los que nadie sabe explicar luego.
ALTER TABLE producto ADD CONSTRAINT ck_producto_motivo CHECK (
    (estado_moderacion = 'RECHAZADO' AND motivo_rechazo IS NOT NULL)
    OR (estado_moderacion <> 'RECHAZADO' AND motivo_rechazo IS NULL)
);

-- Lo de la tienda no pasa por moderación: no tendría sentido que el
-- administrador se aprobara a sí mismo. Si tiene dueño, sí.
ALTER TABLE producto ADD CONSTRAINT ck_producto_tienda_aprobada CHECK (
    propietario_id IS NOT NULL OR estado_moderacion = 'APROBADO'
);

-- ── 3 · Índices ────────────────────────────────────────────────────────────

-- La vitrina filtra SIEMPRE por aprobado. Índice parcial porque el 99 % de las
-- filas lo estarán: uno completo ocuparía lo mismo y no descartaría nada.
CREATE INDEX idx_producto_visible ON producto (id) WHERE estado_moderacion = 'APROBADO';

-- "Mis productos" y la cola de moderación.
CREATE INDEX idx_producto_propietario ON producto (propietario_id, estado_moderacion)
    WHERE propietario_id IS NOT NULL;

-- La cola se ordena por id y no por fecha porque `producto` no tiene columna de
-- creación; con BIGSERIAL el id ya da el orden de llegada.
CREATE INDEX idx_producto_pendiente ON producto (id)
    WHERE estado_moderacion = 'PENDIENTE';

-- ── 4 · Row Level Security ─────────────────────────────────────────────────
--
-- Hasta ahora `producto` no la necesitaba: era vitrina pública y solo el
-- administrador escribía. Ahora escriben terceros, y hay filas que NO deben
-- verse (las pendientes y rechazadas de otro).
--
-- La asimetría es la misma que en `valoracion`, y por eso también aquí se
-- declara una política por comando en vez de una `FOR ALL`:
--
--   LECTURA   -> lo aprobado lo ve cualquiera, identificado o no. Lo pendiente y
--                lo rechazado, solo su dueño y el personal.
--   ESCRITURA -> el colaborador solo toca lo suyo. El personal, todo.

-- `catalogo` solo tenía rls_usuario_actual, rls_es_sistema y rls_es_admin. Hace
-- falta la de personal porque moderar productos es un permiso asignable
-- (PRODUCTOS_GESTIONAR) y puede llevarlo un EMPLEADO: con solo `es_admin`, ese
-- empleado no vería ninguna fila pendiente y no habría ningún error que lo
-- explicara. Misma definición que la de `compras`.
CREATE OR REPLACE FUNCTION catalogo.rls_es_staff() RETURNS BOOLEAN
    LANGUAGE sql STABLE AS $$
    SELECT coalesce(current_setting('app.rol', true), '') IN ('EMPLEADO', 'ADMINISTRADOR');
$$;

ALTER TABLE producto ENABLE ROW LEVEL SECURITY;
ALTER TABLE producto FORCE ROW LEVEL SECURITY;

-- OJO: sin esta política un visitante anónimo no vería NADA y la tienda
-- aparecería vacía. Es el fallo más caro que puede tener este fichero.
CREATE POLICY producto_lectura ON producto
    FOR SELECT USING (
        estado_moderacion = 'APROBADO'
        OR propietario_id = catalogo.rls_usuario_actual()
        OR catalogo.rls_es_staff()
        OR catalogo.rls_es_sistema()
    );

CREATE POLICY producto_alta ON producto
    FOR INSERT WITH CHECK (
        propietario_id = catalogo.rls_usuario_actual()
        OR catalogo.rls_es_staff()
        OR catalogo.rls_es_sistema()
    );

-- El dueño edita lo suyo, pero NO puede aprobárselo: eso lo impide el servicio,
-- que vuelve a poner PENDIENTE en cada edición suya. Aquí solo se decide quién
-- puede tocar la fila.
CREATE POLICY producto_edicion ON producto
    FOR UPDATE USING (
        propietario_id = catalogo.rls_usuario_actual()
        OR catalogo.rls_es_staff()
        OR catalogo.rls_es_sistema()
    ) WITH CHECK (
        propietario_id = catalogo.rls_usuario_actual()
        OR catalogo.rls_es_staff()
        OR catalogo.rls_es_sistema()
    );

CREATE POLICY producto_baja ON producto
    FOR DELETE USING (
        propietario_id = catalogo.rls_usuario_actual()
        OR catalogo.rls_es_staff()
        OR catalogo.rls_es_sistema()
    );

-- `producto_imagen` cuelga del producto y no tiene dueño propio. La subconsulta
-- contra `producto` aplica a su vez la política de arriba, así que la
-- restricción se propaga sola.
ALTER TABLE producto_imagen ENABLE ROW LEVEL SECURITY;
ALTER TABLE producto_imagen FORCE ROW LEVEL SECURITY;

CREATE POLICY producto_imagen_del_producto ON producto_imagen
    USING (EXISTS (SELECT 1 FROM producto p WHERE p.id = producto_imagen.producto_id))
    WITH CHECK (EXISTS (SELECT 1 FROM producto p WHERE p.id = producto_imagen.producto_id));
