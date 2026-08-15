-- ============================================================================
--  Rol de aplicación para que Row Level Security surta efecto
-- ============================================================================
--
--  ESTADO: este rol YA ESTÁ CREADO en la base de datos de desarrollo y el .env
--  local ya apunta a él. Este script queda como referencia reproducible para
--  levantar otro entorno (producción, otra rama, un compañero nuevo).
--
--  POR QUÉ HACE FALTA
--
--  Las políticas RLS (catalogo/V9, compras/V4) se aplican con Flyway, pero
--  Postgres las IGNORA por completo cuando quien se conecta es superusuario o
--  tiene el atributo BYPASSRLS. Y lo hace sin avisar: no hay error, no hay
--  warning, simplemente se ven todas las filas. El rol dueño de Neon es
--  justo ese caso:
--
--      SELECT rolname, rolsuper, rolbypassrls FROM pg_roles WHERE rolname = current_user;
--       neondb_owner | f | t          <-- BYPASSRLS activo
--
--  Por eso la aplicación se conecta con un rol distinto, sin ese atributo, y
--  Flyway sigue usando el dueño (DB_MIGRACION_USER) porque el DDL necesita
--  privilegios que el rol de aplicación no debe tener.
--
--  CÓMO LEVANTARLO EN UN ENTORNO NUEVO
--
--  1. Elige una contraseña y sustitúyela abajo.
--  2. Ejecútalo conectado como el rol DUEÑO:
--
--         psql "$DB_URL_PSQL" -f docker/rol-aplicacion-rls.sql
--
--  3. En el .env:
--         DB_USER / DB_PASSWORD                     -> este rol
--         DB_MIGRACION_USER / DB_MIGRACION_PASSWORD -> el dueño
--
--  4. Arranca. El log debe decir:
--
--         Row Level Security activo en las 7 tablas con datos de usuario
--         Row Level Security activo sobre catalogo.valoracion
--
--     Si dice "RLS INACTIVO", el rol tiene BYPASSRLS y las políticas no se
--     están aplicando.
--
--  El rol NO debe ser dueño de las tablas. Aunque FORCE ROW LEVEL SECURITY
--  hace que las políticas alcancen también al dueño, mantenerlos separados
--  evita que un descuido futuro (un ALTER TABLE ... NO FORCE) devuelva el
--  acceso total sin que nadie se entere.
-- ============================================================================

-- Cambia esta contraseña antes de ejecutar.
CREATE ROLE smartzone_app LOGIN PASSWORD 'CAMBIA_ESTA_CONTRASENA' NOBYPASSRLS NOSUPERUSER NOCREATEDB NOCREATEROLE;

-- Acceso a los tres esquemas.
GRANT USAGE ON SCHEMA catalogo, usuarios, compras TO smartzone_app;

-- Datos: lectura y escritura sobre lo que ya existe.
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA catalogo TO smartzone_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA usuarios TO smartzone_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA compras  TO smartzone_app;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA catalogo TO smartzone_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA usuarios TO smartzone_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA compras  TO smartzone_app;

-- Las funciones que consultan las políticas.
GRANT EXECUTE ON FUNCTION catalogo.rls_usuario_actual(), catalogo.rls_es_sistema(),
    catalogo.rls_es_admin() TO smartzone_app;
GRANT EXECUTE ON FUNCTION compras.rls_usuario_actual(), compras.rls_es_sistema(),
    compras.rls_es_staff() TO smartzone_app;

-- Postgres ya concede EXECUTE a PUBLIC por defecto, así que hoy esto no cambia
-- nada. Se pone igualmente porque el día que alguien endurezca la base con un
-- REVOKE ... FROM PUBLIC, las políticas que faltasen aquí dejarían de poder
-- evaluarse y el servicio devolvería cero filas sin decir por qué.
GRANT EXECUTE ON FUNCTION usuarios.rls_usuario_actual(), usuarios.rls_es_sistema(),
    usuarios.rls_es_staff(), usuarios.rls_es_admin() TO smartzone_app;

-- Y lo que creen las migraciones futuras, para no repetir estos GRANT cada vez.
ALTER DEFAULT PRIVILEGES IN SCHEMA catalogo
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO smartzone_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA usuarios
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO smartzone_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA compras
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO smartzone_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA catalogo GRANT USAGE, SELECT ON SEQUENCES TO smartzone_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA usuarios GRANT USAGE, SELECT ON SEQUENCES TO smartzone_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA compras  GRANT USAGE, SELECT ON SEQUENCES TO smartzone_app;

-- Comprobación: debe devolver f en las dos columnas.
SELECT rolname, rolsuper, rolbypassrls FROM pg_roles WHERE rolname = 'smartzone_app';
