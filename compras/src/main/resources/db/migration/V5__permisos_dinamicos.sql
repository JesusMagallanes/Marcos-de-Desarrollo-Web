-- Personal dinámico en la capa RLS.
--
-- Con roles dinámicos, "es staff" ya no equivale a estar en el par de roles
-- fijos (EMPLEADO, ADMINISTRADOR): un rol con los permisos PEDIDOS_GESTIONAR o
-- ENVIOS_GESTIONAR también gestiona pedidos y envíos. La función `rls_es_staff`
-- pasa a mirar esos permisos, que llegan por el claim `permisos` del JWT (lista
-- separada por comas que DataSourceRls.java vuelca en `app.permisos`).
--
-- Se conserva el nombre clásico de rol por compatibilidad con tokens antiguos
-- que aún no lleven el claim `permisos`.

CREATE OR REPLACE FUNCTION compras.rls_es_staff() RETURNS BOOLEAN
    LANGUAGE sql STABLE AS $$
    SELECT coalesce(current_setting('app.rol', true), '') IN ('EMPLEADO', 'ADMINISTRADOR')
        OR position('PEDIDOS_GESTIONAR' in coalesce(current_setting('app.permisos', true), '')) > 0
        OR position('ENVIOS_GESTIONAR' in coalesce(current_setting('app.permisos', true), '')) > 0;
$$;
