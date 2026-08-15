-- A01: Row Level Security en el servicio de usuarios.
--
-- Es el último de los tres en tenerla, y el que más la necesitaba: aquí viven
-- las contraseñas, los correos y, desde la V5, las fotos de los documentos de
-- identidad. Hasta ahora lo único que impedía que una consulta mal escrita
-- devolviera datos de otro era que el código se acordara de filtrar. Con RLS el
-- filtro lo pone Postgres en el plan de ejecución: aunque la consulta pida
-- `SELECT * FROM documento_identidad`, solo vuelven las filas que tocan.
--
-- Cómo sabe Postgres quién pregunta: la aplicación fija tres variables de sesión
-- al coger la conexión del pool (ver DataSourceRls.java):
--
--   app.usuario_id  -> id del usuario del JWT
--   app.rol         -> su rol
--   app.omitir_rls  -> 'on' solo para trabajo interno sin usuario
--
-- FORCE ROW LEVEL SECURITY es imprescindible: sin él, el dueño de las tablas
-- (que es justo con quien se conecta la aplicación) se salta las políticas y
-- todo esto sería decorativo.
--
-- ── LO QUE HACE ESPECIAL A ESTE SERVICIO ───────────────────────────────────
--
-- En `catalogo` y `compras` toda petición llega ya autenticada. Aquí no: entrar
-- y registrarse son, por definición, operaciones SIN identidad. El login busca
-- por correo a alguien de quien todavía no se sabe nada, y el refresco consulta
-- la lista de tokens revocados sin JWT en contexto.
--
-- Por eso esos caminos se marcan como sistema en Java (ContextoRls.comoSistema).
-- No es una excusa para saltarse RLS: es que autenticar es exactamente la
-- operación que no puede exigir identidad previa. Si no se marcaran, el login
-- diría "credenciales incorrectas" a quien las escribió bien, y —peor— un token
-- revocado pasaría por bueno, porque la consulta que lo busca no vería nada.
--
-- ── AVISO PARA LAS MIGRACIONES SIGUIENTES ──────────────────────────────────
--
-- Cualquier migración que haga INSERT, UPDATE o DELETE sobre estas tablas tiene
-- que empezar por
--
--     SET LOCAL app.omitir_rls = 'on';
--
-- El DDL (columnas, índices, restricciones) no se ve afectado, pero el DML sí:
-- sin esa línea la migración tocaría cero filas y Flyway la daría por aplicada
-- con éxito. Un fallo silencioso de los que se descubren meses después.

-- ── Funciones auxiliares ───────────────────────────────────────────────────
-- Se cualifican con el esquema en las políticas para no depender del
-- search_path de la conexión.

CREATE OR REPLACE FUNCTION usuarios.rls_usuario_actual() RETURNS BIGINT
    LANGUAGE sql STABLE AS $$
    SELECT NULLIF(current_setting('app.usuario_id', true), '')::BIGINT;
$$;

-- El segundo argumento `true` de current_setting devuelve NULL en vez de error
-- cuando la variable no existe: sin él, cualquier consulta desde una conexión
-- sin contexto reventaría en lugar de, simplemente, no ver nada.
CREATE OR REPLACE FUNCTION usuarios.rls_es_sistema() RETURNS BOOLEAN
    LANGUAGE sql STABLE AS $$
    SELECT coalesce(current_setting('app.omitir_rls', true), 'off') = 'on';
$$;

CREATE OR REPLACE FUNCTION usuarios.rls_es_staff() RETURNS BOOLEAN
    LANGUAGE sql STABLE AS $$
    SELECT coalesce(current_setting('app.rol', true), '') IN ('EMPLEADO', 'ADMINISTRADOR');
$$;

-- Se distingue del anterior a propósito. Un empleado atiende pedidos; no tiene
-- por qué ver la foto del DNI de nadie. Donde el dato es especialmente sensible
-- se exige administrador, no "staff".
CREATE OR REPLACE FUNCTION usuarios.rls_es_admin() RETURNS BOOLEAN
    LANGUAGE sql STABLE AS $$
    SELECT coalesce(current_setting('app.rol', true), '') = 'ADMINISTRADOR';
$$;

-- ── usuario ────────────────────────────────────────────────────────────────
-- Cada uno ve su ficha. El personal ve todas porque atiende pedidos y consultas
-- y necesita identificar a quien escribe.
--
-- Las altas (registro y OAuth) llegan siempre por el camino de sistema: crear
-- una cuenta es, otra vez, algo que ocurre antes de que exista identidad.

ALTER TABLE usuarios.usuario ENABLE ROW LEVEL SECURITY;
ALTER TABLE usuarios.usuario FORCE ROW LEVEL SECURITY;

CREATE POLICY usuario_lectura ON usuarios.usuario
    FOR SELECT
    USING (id = usuarios.rls_usuario_actual()
           OR usuarios.rls_es_staff()
           OR usuarios.rls_es_sistema());

-- Escribir es más restrictivo que leer: uno puede cambiar sus propios datos, y
-- sobre los ajenos solo el administrador. Un empleado que puede leer una ficha
-- no debería poder cambiar el rol de nadie.
CREATE POLICY usuario_alta ON usuarios.usuario
    FOR INSERT
    WITH CHECK (usuarios.rls_es_admin() OR usuarios.rls_es_sistema());

CREATE POLICY usuario_edicion ON usuarios.usuario
    FOR UPDATE
    USING (id = usuarios.rls_usuario_actual()
           OR usuarios.rls_es_admin()
           OR usuarios.rls_es_sistema())
    WITH CHECK (id = usuarios.rls_usuario_actual()
           OR usuarios.rls_es_admin()
           OR usuarios.rls_es_sistema());

CREATE POLICY usuario_baja ON usuarios.usuario
    FOR DELETE
    USING (id = usuarios.rls_usuario_actual()
           OR usuarios.rls_es_admin()
           OR usuarios.rls_es_sistema());

-- ── empleado ───────────────────────────────────────────────────────────────
-- Quién es personal y con qué cargo. No lo consulta ninguna entidad JPA hoy,
-- pero se protege igual: una tabla sin políticas es una puerta abierta esperando
-- a que alguien escriba el repositorio que la use.

ALTER TABLE usuarios.empleado ENABLE ROW LEVEL SECURITY;
ALTER TABLE usuarios.empleado FORCE ROW LEVEL SECURITY;

CREATE POLICY empleado_propio ON usuarios.empleado
    USING (usuario_id = usuarios.rls_usuario_actual()
           OR usuarios.rls_es_admin()
           OR usuarios.rls_es_sistema())
    WITH CHECK (usuarios.rls_es_admin() OR usuarios.rls_es_sistema());

-- ── solicitud_colaborador ──────────────────────────────────────────────────
-- El solicitante ve la suya; el administrador, todas, porque es quien resuelve.
-- Un empleado NO: la solicitud lleva domicilio y documento de identidad.

ALTER TABLE usuarios.solicitud_colaborador ENABLE ROW LEVEL SECURITY;
ALTER TABLE usuarios.solicitud_colaborador FORCE ROW LEVEL SECURITY;

CREATE POLICY solicitud_lectura ON usuarios.solicitud_colaborador
    FOR SELECT
    USING (usuario_id = usuarios.rls_usuario_actual()
           OR usuarios.rls_es_admin()
           OR usuarios.rls_es_sistema());

-- Solo se puede crear una solicitud a nombre propio. Esto es lo que convierte
-- en imposible, y no solo en "no implementado", que alguien solicite por otro.
CREATE POLICY solicitud_alta ON usuarios.solicitud_colaborador
    FOR INSERT
    WITH CHECK (usuario_id = usuarios.rls_usuario_actual()
           OR usuarios.rls_es_sistema());

-- Resolver es cosa del administrador. El solicitante no puede tocar la suya una
-- vez enviada: si se equivocó, se rechaza y manda otra.
CREATE POLICY solicitud_resolucion ON usuarios.solicitud_colaborador
    FOR UPDATE
    USING (usuarios.rls_es_admin() OR usuarios.rls_es_sistema())
    WITH CHECK (usuarios.rls_es_admin() OR usuarios.rls_es_sistema());

CREATE POLICY solicitud_baja ON usuarios.solicitud_colaborador
    FOR DELETE
    USING (usuarios.rls_es_admin() OR usuarios.rls_es_sistema());

-- ── documento_identidad ────────────────────────────────────────────────────
-- El dato más sensible de todo el sistema. La regla es la más estrecha: su
-- dueño y el administrador. Nadie más, ni siquiera el personal.

ALTER TABLE usuarios.documento_identidad ENABLE ROW LEVEL SECURITY;
ALTER TABLE usuarios.documento_identidad FORCE ROW LEVEL SECURITY;

CREATE POLICY documento_lectura ON usuarios.documento_identidad
    FOR SELECT
    USING (usuario_id = usuarios.rls_usuario_actual()
           OR usuarios.rls_es_admin()
           OR usuarios.rls_es_sistema());

CREATE POLICY documento_alta ON usuarios.documento_identidad
    FOR INSERT
    WITH CHECK (usuario_id = usuarios.rls_usuario_actual()
           OR usuarios.rls_es_sistema());

-- El dueño actualiza los suyos (al enviar la solicitud, que es cuando se les
-- asigna `solicitud_id`). El sistema, además, para marcarlos purgados.
CREATE POLICY documento_edicion ON usuarios.documento_identidad
    FOR UPDATE
    USING (usuario_id = usuarios.rls_usuario_actual()
           OR usuarios.rls_es_sistema())
    WITH CHECK (usuario_id = usuarios.rls_usuario_actual()
           OR usuarios.rls_es_sistema());

CREATE POLICY documento_baja ON usuarios.documento_identidad
    FOR DELETE
    USING (usuario_id = usuarios.rls_usuario_actual()
           OR usuarios.rls_es_sistema());

-- ── token_revocado ─────────────────────────────────────────────────────────
-- La lista negra de tokens.
--
-- OJO CON ESTA: es la única cuya consulta ocurre SIEMPRE sin usuario en
-- contexto, porque comprobar si un refresh token está revocado pasa antes de
-- que haya sesión. Si esa consulta no viera las filas, un token revocado
-- pasaría por bueno: el fallo abriría en lugar de cerrar.
--
-- Funciona porque AuthService entero corre como sistema. La política se pone de
-- todas formas para que nadie pueda listar los jti de otro si algún día se
-- expone por otra vía.

ALTER TABLE usuarios.token_revocado ENABLE ROW LEVEL SECURITY;
ALTER TABLE usuarios.token_revocado FORCE ROW LEVEL SECURITY;

CREATE POLICY token_revocado_propio ON usuarios.token_revocado
    USING (usuario_id = usuarios.rls_usuario_actual()
           OR usuarios.rls_es_sistema())
    WITH CHECK (usuario_id = usuarios.rls_usuario_actual()
           OR usuarios.rls_es_sistema());
