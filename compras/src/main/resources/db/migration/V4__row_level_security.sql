-- A01: Row Level Security. La última barrera, la que vive en la base de datos.
--
-- El código ya filtra por usuario (los servicios reciben el `usuarioId` del JWT
-- y jamás lo aceptan por URL). El problema de esa defensa es que depende de que
-- CADA consulta, hoy y dentro de un año, se acuerde de poner el WHERE. Un
-- `findAll()` colado en un repositorio, un método nuevo que olvida el filtro o
-- una inyección SQL en cualquier punto se llevan por delante todo el modelo de
-- acceso. Con RLS el filtro lo pone Postgres en el plan de ejecución: aunque la
-- consulta pida `SELECT * FROM pedido`, solo vuelven las filas del usuario en
-- curso.
--
-- Cómo sabe Postgres quién es el usuario: la aplicación fija tres variables de
-- sesión al coger la conexión del pool (ver DataSourceRls.java):
--
--   app.usuario_id  -> id del usuario del JWT
--   app.rol         -> su rol
--   app.omitir_rls  -> 'on' solo para trabajos internos sin usuario (la saga)
--
-- FORCE ROW LEVEL SECURITY es imprescindible: sin él, el dueño de las tablas
-- (que es justo el usuario con el que se conecta la aplicación) se salta las
-- políticas y todo esto sería decorativo.
--
-- AVISO PARA LAS MIGRACIONES SIGUIENTES: a partir de aquí, cualquier migración
-- que haga INSERT, UPDATE o DELETE sobre estas tablas tiene que empezar por
--
--     SET LOCAL app.omitir_rls = 'on';
--
-- El DDL (crear columnas, índices, restricciones) no se ve afectado, pero el
-- DML sí: sin esa línea la migración no vería ninguna fila, tocaría cero
-- registros y Flyway la daría por aplicada con éxito.

-- ── Funciones auxiliares ────────────────────────────────────────
-- Se cualifican con el esquema en las políticas para no depender del
-- search_path de la conexión.

CREATE OR REPLACE FUNCTION compras.rls_usuario_actual() RETURNS BIGINT
    LANGUAGE sql STABLE AS $$
    SELECT NULLIF(current_setting('app.usuario_id', true), '')::BIGINT;
$$;

-- El segundo argumento `true` de current_setting devuelve NULL en vez de error
-- cuando la variable no existe: sin él, cualquier consulta desde una conexión
-- sin contexto reventaría en lugar de, simplemente, no ver nada.
CREATE OR REPLACE FUNCTION compras.rls_es_sistema() RETURNS BOOLEAN
    LANGUAGE sql STABLE AS $$
    SELECT coalesce(current_setting('app.omitir_rls', true), 'off') = 'on';
$$;

CREATE OR REPLACE FUNCTION compras.rls_es_staff() RETURNS BOOLEAN
    LANGUAGE sql STABLE AS $$
    SELECT coalesce(current_setting('app.rol', true), '') IN ('EMPLEADO', 'ADMINISTRADOR');
$$;

-- ── Carrito ─────────────────────────────────────────────────────
-- Estricto: el carrito es solo del dueño. Ni siquiera un administrador tiene por
-- qué ver lo que otro tiene a medio comprar.

ALTER TABLE compras.carrito ENABLE ROW LEVEL SECURITY;
ALTER TABLE compras.carrito FORCE ROW LEVEL SECURITY;

CREATE POLICY carrito_propio ON compras.carrito
    USING (usuario_id = compras.rls_usuario_actual() OR compras.rls_es_sistema())
    WITH CHECK (usuario_id = compras.rls_usuario_actual() OR compras.rls_es_sistema());

-- carrito_item no tiene usuario_id: cuelga del carrito. La subconsulta contra
-- `carrito` aplica a su vez la política de arriba, así que la restricción se
-- propaga sola.
ALTER TABLE compras.carrito_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE compras.carrito_item FORCE ROW LEVEL SECURITY;

CREATE POLICY carrito_item_propio ON compras.carrito_item
    USING (EXISTS (
        SELECT 1 FROM compras.carrito c
        WHERE c.id = carrito_item.carrito_id
          AND (c.usuario_id = compras.rls_usuario_actual() OR compras.rls_es_sistema())))
    WITH CHECK (EXISTS (
        SELECT 1 FROM compras.carrito c
        WHERE c.id = carrito_item.carrito_id
          AND (c.usuario_id = compras.rls_usuario_actual() OR compras.rls_es_sistema())));

-- ── Pedidos ─────────────────────────────────────────────────────
-- Aquí sí entra el personal: EMPLEADO y ADMINISTRADOR gestionan estados y
-- listan pedidos ajenos, y esa es una función legítima del panel.

ALTER TABLE compras.pedido ENABLE ROW LEVEL SECURITY;
ALTER TABLE compras.pedido FORCE ROW LEVEL SECURITY;

CREATE POLICY pedido_propio_o_staff ON compras.pedido
    USING (usuario_id = compras.rls_usuario_actual()
        OR compras.rls_es_staff() OR compras.rls_es_sistema())
    WITH CHECK (usuario_id = compras.rls_usuario_actual()
        OR compras.rls_es_staff() OR compras.rls_es_sistema());

ALTER TABLE compras.detalle_pedido ENABLE ROW LEVEL SECURITY;
ALTER TABLE compras.detalle_pedido FORCE ROW LEVEL SECURITY;

CREATE POLICY detalle_pedido_propio_o_staff ON compras.detalle_pedido
    USING (EXISTS (
        SELECT 1 FROM compras.pedido p
        WHERE p.id = detalle_pedido.pedido_id
          AND (p.usuario_id = compras.rls_usuario_actual()
               OR compras.rls_es_staff() OR compras.rls_es_sistema())))
    WITH CHECK (EXISTS (
        SELECT 1 FROM compras.pedido p
        WHERE p.id = detalle_pedido.pedido_id
          AND (p.usuario_id = compras.rls_usuario_actual()
               OR compras.rls_es_staff() OR compras.rls_es_sistema())));

ALTER TABLE compras.envios ENABLE ROW LEVEL SECURITY;
ALTER TABLE compras.envios FORCE ROW LEVEL SECURITY;

CREATE POLICY envio_propio_o_staff ON compras.envios
    USING (EXISTS (
        SELECT 1 FROM compras.pedido p
        WHERE p.id = envios.pedido_id
          AND (p.usuario_id = compras.rls_usuario_actual()
               OR compras.rls_es_staff() OR compras.rls_es_sistema())))
    WITH CHECK (EXISTS (
        SELECT 1 FROM compras.pedido p
        WHERE p.id = envios.pedido_id
          AND (p.usuario_id = compras.rls_usuario_actual()
               OR compras.rls_es_staff() OR compras.rls_es_sistema())));

-- ── Saga e idempotencia ─────────────────────────────────────────
-- El barrendero de sagas abandonadas corre sin usuario: entra por
-- rls_es_sistema(), que la aplicación activa solo para tareas programadas.

ALTER TABLE compras.saga_checkout ENABLE ROW LEVEL SECURITY;
ALTER TABLE compras.saga_checkout FORCE ROW LEVEL SECURITY;

CREATE POLICY saga_propia ON compras.saga_checkout
    USING (usuario_id = compras.rls_usuario_actual() OR compras.rls_es_sistema())
    WITH CHECK (usuario_id = compras.rls_usuario_actual() OR compras.rls_es_sistema());

ALTER TABLE compras.clave_idempotencia ENABLE ROW LEVEL SECURITY;
ALTER TABLE compras.clave_idempotencia FORCE ROW LEVEL SECURITY;

CREATE POLICY idempotencia_propia ON compras.clave_idempotencia
    USING (usuario_id = compras.rls_usuario_actual() OR compras.rls_es_sistema())
    WITH CHECK (usuario_id = compras.rls_usuario_actual() OR compras.rls_es_sistema());

-- `metodopago` queda deliberadamente FUERA: es un catálogo público de opciones
-- de pago, igual para todos. Poner RLS ahí solo añadiría coste sin proteger nada.
