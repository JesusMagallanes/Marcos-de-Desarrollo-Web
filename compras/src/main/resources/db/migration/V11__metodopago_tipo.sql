-- Columna para identificar el tipo de pasarela de pago sin depender del nombre.
-- Permite que `esMercadoPago()` sea una comparación de enum en vez de un
-- contains() sobre el nombre, que fallaría si alguien cambia el texto.

ALTER TABLE metodopago ADD COLUMN tipo VARCHAR(30) NOT NULL DEFAULT 'OTRO';

-- Migrar los existentes: si el nombre contiene "mercadopago" (sin espacios,
-- en minúsculas), es MercadoPago.
UPDATE metodopago
   SET tipo = 'MERCADOPAGO'
 WHERE LOWER(REPLACE(name, ' ', '')) LIKE '%mercadopago%';

-- Y el que se cobra al recibir, EFECTIVO. Dejarlo en OTRO funcionaba de
-- casualidad —lo único que se pregunta hoy es si NO es MercadoPago—, pero
-- OTRO significa "no sé qué es esto" y aquí sí se sabe.
UPDATE metodopago
   SET tipo = 'EFECTIVO'
 WHERE tipo = 'OTRO'
   AND LOWER(name) LIKE '%contra entrega%';
