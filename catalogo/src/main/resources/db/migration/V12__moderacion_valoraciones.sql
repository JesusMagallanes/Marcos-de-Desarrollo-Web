-- Moderación de valoraciones: ningún comentario se publica hasta que un
-- administrador lo aprueba.
--
-- Cómo funciona:
--   * `estado` nace PENDIENTE: el servicio lo fija al crear o re-valorar, así
--     que un cliente nunca puede estrenar una valoración aprobada.
--   * La tienda (ficha del producto y promedios) solo lee las APROBADA.
--   * El panel de administración ve todas y aprueba/rechaza/elimina.
--
-- AVISO de RLS: esta migración hace DML sobre `valoracion` (el UPDATE que deja
-- Aprobadas las reseñas existentes). Con FORCE ROW LEVEL SECURITY, sin el
-- `SET LOCAL` de la primera línea esa sentencia tocaría cero filas y la
-- migración se daría por aplicada con éxito. Ver V9__row_level_security.sql.
SET LOCAL app.omitir_rls = 'on';

ALTER TABLE valoracion
    ADD COLUMN estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE';

-- Las reseñas que ya estaban publicadas antes de la moderación siguen
-- visibles: fueron validadas en su momento y no hay que volver a revisarlas.
UPDATE valoracion SET estado = 'APROBADA';

ALTER TABLE valoracion
    ADD CONSTRAINT ck_valoracion_estado CHECK (estado IN ('PENDIENTE', 'APROBADA', 'RECHAZADA'));

CREATE INDEX idx_valoracion_estado ON valoracion (estado, producto_id);

-- La moderación exige que el ADMINISTRADOR pueda cambiar el `estado` de una
-- valoración ajena. La política anterior solo dejaba editar al dueño, así que
-- el panel no tendría forma de aprobar ni rechazar. Se amplía a admin (y al
-- sistema); el servicio nunca reasigna `usuario_id`, el WITH CHECK solo sirve
-- de red de seguridad contra un servicio roto.
DROP POLICY valoracion_edicion_propia ON catalogo.valoracion;

CREATE POLICY valoracion_edicion_propia ON catalogo.valoracion
    FOR UPDATE
    USING (
        usuario_id = catalogo.rls_usuario_actual()
        OR catalogo.rls_es_admin()
        OR catalogo.rls_es_sistema())
    WITH CHECK (
        usuario_id = catalogo.rls_usuario_actual()
        OR catalogo.rls_es_admin()
        OR catalogo.rls_es_sistema());
