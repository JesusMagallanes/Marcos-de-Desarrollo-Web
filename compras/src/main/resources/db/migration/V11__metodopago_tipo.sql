-- Columna para identificar el tipo de pasarela de pago sin depender del nombre.
-- Permite que `esMercadoPago()` sea una comparación de enum en vez de un
-- contains() sobre el nombre, que fallaría si alguien cambia el texto.

ALTER TABLE metodopago ADD COLUMN tipo VARCHAR(30) NOT NULL DEFAULT 'OTRO';

-- Migrar los existentes: si el nombre contiene "mercadopago" (sin espacios,
-- en minúsculas), es MercadoPago.
UPDATE metodopago
   SET tipo = 'MERCADOPAGO'
 WHERE LOWER(REPLACE(name, ' ', '')) LIKE '%mercadopago%';
