-- El método que se cobra al recibir queda marcado como EFECTIVO.
--
-- Dejarlo en OTRO funcionaba de casualidad: lo único que se pregunta hoy es si
-- NO es MercadoPago. Pero OTRO significa "no sé qué es esto", y aquí sí se sabe.
--
-- Va en una migración nueva y no dentro de la V11 a propósito. La V11 pudo
-- aplicarse ya en algún entorno, y editar una migración aplicada le cambia el
-- checksum: Flyway responde con «Validate failed: Migrations have failed
-- validation» y el servicio deja de arrancar. Una migración aplicada no se
-- toca; se añade la siguiente.

UPDATE metodopago
   SET tipo = 'EFECTIVO'
 WHERE tipo = 'OTRO'
   AND LOWER(name) LIKE '%contra entrega%';
