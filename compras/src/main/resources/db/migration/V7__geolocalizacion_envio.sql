-- Épica 3: cuánto se tarda en llegar al punto de entrega.
--
-- Lo que pide el negocio: que quien prepara un envío vea, desde la dirección de
-- la tienda, a qué distancia está el destino y cuánto va a costarle llegar. Con
-- eso se agrupan repartos y se decide qué sale antes.
--
-- Las coordenadas las da el navegador del comprador si acepta compartir su
-- ubicación al pagar. Son OPCIONALES a propósito: nadie se queda sin comprar por
-- no querer dar su posición, y sin ellas el envío funciona igual, solo que sin
-- el cálculo.

ALTER TABLE saga_checkout ADD COLUMN latitud  NUMERIC(9, 6);
ALTER TABLE saga_checkout ADD COLUMN longitud NUMERIC(9, 6);

ALTER TABLE envios ADD COLUMN latitud  NUMERIC(9, 6);
ALTER TABLE envios ADD COLUMN longitud NUMERIC(9, 6);

COMMENT ON COLUMN envios.latitud IS
    'Posición del punto de entrega, si el comprador la compartió. NULL = no la dio.';

-- NUMERIC(9,6) y no coma flotante: seis decimales son ~11 cm, de sobra para una
-- entrega, y un tipo exacto evita que dos lecturas de la misma fila den valores
-- distintos por el redondeo binario. La misma razón por la que el dinero es
-- NUMERIC en este proyecto.

-- Rango válido de la Tierra. Un valor fuera de esto no es una coordenada: es un
-- error de unidades o algo manipulado, y conviene que salte aquí y no cuando
-- alguien vea un reparto a 4.000 km.
ALTER TABLE envios ADD CONSTRAINT ck_envio_coordenadas CHECK (
    (latitud IS NULL AND longitud IS NULL)
    OR (latitud BETWEEN -90 AND 90 AND longitud BETWEEN -180 AND 180)
);

ALTER TABLE saga_checkout ADD CONSTRAINT ck_saga_coordenadas CHECK (
    (latitud IS NULL AND longitud IS NULL)
    OR (latitud BETWEEN -90 AND 90 AND longitud BETWEEN -180 AND 180)
);

-- Las dos van juntas o no van: media coordenada no sirve para nada y tenerla
-- guardada solo invita a que alguien la use pensando que está completa.
