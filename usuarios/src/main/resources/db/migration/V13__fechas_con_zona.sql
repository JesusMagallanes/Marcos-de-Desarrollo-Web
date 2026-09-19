-- ============================================================================
--  Fechas con zona horaria
-- ============================================================================
--
--  Cinco columnas guardaban TIMESTAMP sin zona, y dos de ellas deciden cosas:
--  `token_revocado.expira_en` (cuando se puede purgar un token) y
--  `usuario.tokens_validos_desde` (el corte que invalida las sesiones tras un
--  reuso de refresh token). Con la JVM en una zona y Postgres en otra, ese
--  corte se compara contra una hora de pared que no es la misma, y un token
--  que deberia estar fuera sigue valiendo cinco horas mas. TIMESTAMPTZ guarda
--  el instante; las entidades pasan a Instant.
--
--  Como en V17 de catalogo, lo existente se reinterpreta con la zona de la
--  sesion (UTC en los contenedores).
ALTER TABLE usuario        ALTER COLUMN creado_en            TYPE TIMESTAMP WITH TIME ZONE,
                           ALTER COLUMN tokens_validos_desde TYPE TIMESTAMP WITH TIME ZONE;
ALTER TABLE token_revocado ALTER COLUMN expira_en            TYPE TIMESTAMP WITH TIME ZONE,
                           ALTER COLUMN creado_en            TYPE TIMESTAMP WITH TIME ZONE;
ALTER TABLE rol            ALTER COLUMN creado_en            TYPE TIMESTAMP WITH TIME ZONE;
