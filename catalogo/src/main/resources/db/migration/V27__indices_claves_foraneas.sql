-- Postgres no indexa una clave foranea por su cuenta. Estas cuatro se usan en
-- JOIN o en borrados en cascada y no tenian indice: hoy las tablas son
-- pequenas y no se nota; con volumen cada consulta por ellas recorreria la
-- tabla entera.
CREATE INDEX IF NOT EXISTS idx_reserva_stock_producto      ON reserva_stock (producto_id);
CREATE INDEX IF NOT EXISTS idx_evento_interaccion_categoria ON evento_interaccion (categoria_id);
CREATE INDEX IF NOT EXISTS idx_sujeto_similitud_b          ON sujeto_similitud (sujeto_b);
CREATE INDEX IF NOT EXISTS idx_sujeto_fusionado            ON sujeto (fusionado_en);
