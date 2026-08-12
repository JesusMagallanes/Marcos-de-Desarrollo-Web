-- A01: Row Level Security sobre las valoraciones.
--
-- En catálogo casi todo es vitrina pública (productos, categorías, marcas): ahí
-- RLS no pinta nada, porque el dato es de todos. La excepción es `valoracion`,
-- que sí lleva `usuario_id` y sí tiene la regla "cada cliente solo toca la suya".
--
-- Y esa regla tiene una asimetría que las políticas reflejan:
--
--   LECTURA  -> pública. Las opiniones se muestran a cualquiera que entre en la
--               ficha del producto, esté o no identificado. Si el SELECT
--               filtrara por usuario, la tienda no mostraría ninguna reseña a los
--               visitantes anónimos.
--   ESCRITURA-> solo el dueño. Nadie puede crear, editar ni borrar la valoración
--               de otro, ni cambiando el id en la petición.
--
-- Por eso NO se usa una política `FOR ALL`: haría falta que el USING valiera
-- para leer y a la vez restringiera al escribir, y son condiciones distintas.
-- Se declara una por comando.
--
-- El contexto (`app.usuario_id`) lo fija la aplicación al coger la conexión;
-- ver DataSourceRls.java.
--
-- AVISO PARA LAS MIGRACIONES SIGUIENTES: cualquier migración que haga INSERT,
-- UPDATE o DELETE sobre `valoracion` tiene que empezar por
--
--     SET LOCAL app.omitir_rls = 'on';
--
-- El DDL no se ve afectado, pero el DML sí: sin esa línea la migración tocaría
-- cero filas y Flyway la daría por aplicada con éxito.

CREATE OR REPLACE FUNCTION catalogo.rls_usuario_actual() RETURNS BIGINT
    LANGUAGE sql STABLE AS $$
    SELECT NULLIF(current_setting('app.usuario_id', true), '')::BIGINT;
$$;

CREATE OR REPLACE FUNCTION catalogo.rls_es_sistema() RETURNS BOOLEAN
    LANGUAGE sql STABLE AS $$
    SELECT coalesce(current_setting('app.omitir_rls', true), 'off') = 'on';
$$;

CREATE OR REPLACE FUNCTION catalogo.rls_es_admin() RETURNS BOOLEAN
    LANGUAGE sql STABLE AS $$
    SELECT coalesce(current_setting('app.rol', true), '') = 'ADMINISTRADOR';
$$;

ALTER TABLE catalogo.valoracion ENABLE ROW LEVEL SECURITY;
ALTER TABLE catalogo.valoracion FORCE ROW LEVEL SECURITY;

-- Lectura abierta: las reseñas son parte de la ficha pública del producto.
CREATE POLICY valoracion_lectura_publica ON catalogo.valoracion
    FOR SELECT USING (true);

-- Alta: la fila que se inserta tiene que ser del usuario en curso. Aunque el
-- servicio se equivocara y pasara otro usuario_id, Postgres lo rechaza.
CREATE POLICY valoracion_alta_propia ON catalogo.valoracion
    FOR INSERT WITH CHECK (
        usuario_id = catalogo.rls_usuario_actual() OR catalogo.rls_es_sistema());

-- Edición: solo la propia, y no se puede reasignar a otro usuario (USING mira la
-- fila antes del cambio; WITH CHECK, después).
CREATE POLICY valoracion_edicion_propia ON catalogo.valoracion
    FOR UPDATE
    USING (usuario_id = catalogo.rls_usuario_actual() OR catalogo.rls_es_sistema())
    WITH CHECK (usuario_id = catalogo.rls_usuario_actual() OR catalogo.rls_es_sistema());

-- Borrado: el dueño, o un administrador para poder retirar una reseña abusiva.
CREATE POLICY valoracion_borrado_propio ON catalogo.valoracion
    FOR DELETE USING (
        usuario_id = catalogo.rls_usuario_actual()
        OR catalogo.rls_es_admin() OR catalogo.rls_es_sistema());
