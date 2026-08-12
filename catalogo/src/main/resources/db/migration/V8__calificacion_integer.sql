-- La calificación pasa de SMALLINT a INTEGER.
--
-- V7 creó la columna como SMALLINT, que en Java se mapea a Short. La entidad y
-- el DTO trabajan con Integer, así que Hibernate fallaba la validación de
-- esquema al arrancar (`ddl-auto=validate`): "found smallint, but expecting
-- integer". Ampliar la columna es más sencillo que estrechar el tipo en Java, y
-- el rango 1..5 cabe de sobra en cualquiera de los dos.
--
-- El CHECK de rango lo mantiene Postgres; no hace falta recrearlo.

ALTER TABLE catalogo.valoracion
    ALTER COLUMN calificacion TYPE INTEGER;
