-- Permisos dinámicos en la capa RLS.
--
-- Con roles dinámicos (rol_usuario.rol pasa a ser una clave foránea a una tabla
-- `rol` con permisos), moderar valoraciones ya no es exclusivo de
-- ADMINISTRADOR: quien tenga el permiso VALORACIONES_GESTIONAR puede editar
-- estado y retirar reseñas. Esta migración redefine `rls_es_admin` para que
-- admita el rol clásico o ese permiso, y queda pendiente de comprobarse desde
-- el permiso, no solo desde el nombre de rol.
--
-- El contexto `app.permisos` lo fija DataSourceRls.java a partir del claim
-- `permisos` del JWT (lista separada por comas). Un rol sin el permiso sigue
-- sin poder moderar, aunque el código del controlador se equivocara y dejara
-- pasar la petición: RLS es la segunda barrera.

CREATE OR REPLACE FUNCTION catalogo.rls_es_admin() RETURNS BOOLEAN
    LANGUAGE sql STABLE AS $$
    SELECT coalesce(current_setting('app.rol', true), '') = 'ADMINISTRADOR'
        OR position('VALORACIONES_GESTIONAR' in coalesce(current_setting('app.permisos', true), '')) > 0;
$$;
