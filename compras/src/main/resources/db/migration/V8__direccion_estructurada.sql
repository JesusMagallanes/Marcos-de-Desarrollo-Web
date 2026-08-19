-- La direccion de entrega deja de ser una linea de texto y pasa a tener partes.
--
-- Hasta ahora era un VARCHAR(200) donde el comprador escribia lo que queria.
-- Sirve para imprimir una etiqueta, pero no para nada mas: no se puede calcular
-- un costo de envio por codigo postal, no se puede agrupar reparto por distrito
-- y, sobre todo, no se le puede mandar a la pasarela.
--
-- MercadoPago acepta `shipments.receiver_address` con la direccion en campos
-- separados (zip_code, street_name, street_number, city, state, country...) y
-- con eso calcula el envio y se lo enseña al comprador ya en su pantalla. Una
-- cadena suelta no encaja en ninguno de esos campos, asi que hasta ahora no se
-- mandaba nada.
--
-- La jerarquia es la de Peru --departamento > provincia > distrito-- y se mapea
-- a la de la pasarela como state > city > neighborhood, que es la equivalencia
-- que usa MercadoLibre en MPE.

-- ── La saga la lleva desde el inicio hasta que crea el envio ────────────────

ALTER TABLE saga_checkout
    ADD COLUMN calle            VARCHAR(200),
    ADD COLUMN numero           VARCHAR(20),
    ADD COLUMN codigo_postal    VARCHAR(10),
    ADD COLUMN distrito         VARCHAR(80),
    ADD COLUMN provincia        VARCHAR(80),
    ADD COLUMN departamento     VARCHAR(80),
    -- VARCHAR(2) y no CHAR(2): en Postgres CHAR es `bpchar`, un tipo distinto,
    -- y la validacion de esquema de Hibernate rechaza el arranque entero por
    -- eso. Pasa lo mismo en usuarios y ya costo un arranque en rojo.
    ADD COLUMN pais             VARCHAR(2),
    ADD COLUMN receptor_nombre  VARCHAR(120);

-- ── El envio, que es donde acaba y donde la lee quien reparte ───────────────

ALTER TABLE envios
    ADD COLUMN calle            VARCHAR(200),
    ADD COLUMN numero           VARCHAR(20),
    ADD COLUMN codigo_postal    VARCHAR(10),
    ADD COLUMN distrito         VARCHAR(80),
    ADD COLUMN provincia        VARCHAR(80),
    ADD COLUMN departamento     VARCHAR(80),
    -- VARCHAR(2) y no CHAR(2): en Postgres CHAR es `bpchar`, un tipo distinto,
    -- y la validacion de esquema de Hibernate rechaza el arranque entero por
    -- eso. Pasa lo mismo en usuarios y ya costo un arranque en rojo.
    ADD COLUMN pais             VARCHAR(2),
    ADD COLUMN receptor_nombre  VARCHAR(120);

-- Todo NULL-able y `direccion` se queda: los envios que ya existen no tienen
-- estas partes y no hay forma honesta de inventarselas. `direccion` sigue
-- siendo la linea que se imprime --ahora compuesta a partir de las partes-- asi
-- que las pantallas de reparto siguen funcionando sin tocar nada.

COMMENT ON COLUMN envios.codigo_postal IS
    'Clave del costo de envio. Es lo primero que mira cualquier paqueteria.';
COMMENT ON COLUMN envios.pais IS
    'ISO 3166-1 alfa-2. PE salvo que algun dia se venda fuera.';
COMMENT ON COLUMN envios.receptor_nombre IS
    'Quien recibe, que no siempre es quien compra: regalos y envios a la oficina.';

-- El codigo postal peruano son cinco digitos. Se comprueba aqui ademas de en la
-- validacion de Java porque esta tabla tambien la tocan migraciones y scripts, y
-- un codigo mal formado no se descubre hasta que la paqueteria devuelve el
-- paquete.
ALTER TABLE envios ADD CONSTRAINT ck_envio_codigo_postal
    CHECK (codigo_postal IS NULL OR codigo_postal ~ '^[0-9]{5}$');

ALTER TABLE saga_checkout ADD CONSTRAINT ck_saga_codigo_postal
    CHECK (codigo_postal IS NULL OR codigo_postal ~ '^[0-9]{5}$');
