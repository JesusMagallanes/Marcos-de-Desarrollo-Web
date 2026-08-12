-- La vigencia de la oferta se guarda como instante con zona horaria: el
-- "ahora" del servidor y las fechas elegidas en la tienda deben compararse en
-- el mismo instante, sin depender de la zona horaria del contenedor (UTC).
-- Los valores existentes se reinterpretan con la zona de la sesión actual.

ALTER TABLE producto
    ALTER COLUMN oferta_inicio TYPE TIMESTAMP WITH TIME ZONE,
    ALTER COLUMN oferta_fin TYPE TIMESTAMP WITH TIME ZONE;
