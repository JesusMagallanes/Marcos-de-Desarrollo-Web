-- El pedido guarda de qué se compone su total, no solo cuánto suma.
--
-- Hasta ahora `pedido` solo tenía `total`, y las líneas de `detalle_pedido`
-- suman el subtotal: mientras el envío no se cobraba, los dos números
-- coincidían y nadie notó que faltaba el desglose. Ahora que sí se cobra, el
-- detalle de una compra enseñaba líneas por S/ 200 y un total de S/ 215, con la
-- diferencia sin explicar en ninguna parte.
--
-- Se guarda en vez de calcularse restando (`total - suma de líneas`) porque
-- restar convierte cualquier descuadre futuro en un costo de envío inventado,
-- silencioso y con pinta de dato bueno. Un pedido es un documento: lo que se le
-- cobró al comprador se escribe, no se deduce.
--
-- Los pedidos que ya existen se rellenan con el subtotal igual al total y envío
-- cero, que es exactamente lo que se les cobró: el envío no llegó a cobrarse
-- nunca. Reescribirlos con la tarifa de hoy sería falsear el histórico.

ALTER TABLE pedido ADD COLUMN subtotal    NUMERIC(12, 2);
ALTER TABLE pedido ADD COLUMN costo_envio NUMERIC(12, 2);

-- DML sobre una tabla con RLS: sin esto la migración tocaría cero filas y
-- Flyway la daría por buena (ver el aviso de V4__row_level_security.sql).
SET LOCAL app.omitir_rls = 'on';

UPDATE pedido SET subtotal = total, costo_envio = 0
 WHERE subtotal IS NULL;

ALTER TABLE pedido ALTER COLUMN subtotal    SET NOT NULL;
ALTER TABLE pedido ALTER COLUMN costo_envio SET NOT NULL;

ALTER TABLE pedido ADD CONSTRAINT ck_pedido_importes
    CHECK (subtotal >= 0 AND costo_envio >= 0 AND total = subtotal + costo_envio);
