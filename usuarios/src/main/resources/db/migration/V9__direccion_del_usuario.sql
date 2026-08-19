-- La direccion de entrega pasa a vivir en el perfil.
--
-- Hasta ahora se preguntaba en el carrito, cada vez, y no se guardaba en ningun
-- sitio: la misma persona reescribia su casa en cada compra. Ademas el unico
-- dato que quedaba era `address`, una linea de texto libre que sirve para
-- imprimir una etiqueta y para nada mas.
--
-- Ahora se guarda una vez, en partes, y el checkout solo pregunta si el pedido
-- va ahi o a otro sitio.
--
-- La jerarquia es la de Peru: departamento > provincia > distrito.

ALTER TABLE usuario
    ADD COLUMN dir_calle          VARCHAR(200),
    ADD COLUMN dir_numero         VARCHAR(20),
    ADD COLUMN dir_referencia     VARCHAR(200),
    ADD COLUMN dir_codigo_postal  VARCHAR(10),
    ADD COLUMN dir_distrito       VARCHAR(80),
    ADD COLUMN dir_provincia      VARCHAR(80),
    ADD COLUMN dir_departamento   VARCHAR(80),
    -- VARCHAR(2) y no CHAR(2): en Postgres CHAR es `bpchar`, un tipo distinto, y
    -- la validacion de esquema de Hibernate tira el arranque entero por eso.
    ADD COLUMN dir_pais           VARCHAR(2),
    ADD COLUMN dir_latitud        DECIMAL(9, 6),
    ADD COLUMN dir_longitud       DECIMAL(9, 6);

-- `address` se queda: es la linea que se imprime, y a partir de ahora se compone
-- desde las partes en vez de escribirla el usuario a mano. Asi lo que se imprime
-- y lo que se le manda a la pasarela no pueden contradecirse, y las cuentas que
-- ya existen conservan lo que tenian escrito.

COMMENT ON COLUMN usuario.address IS
    'Linea de entrega ya compuesta. Se deriva de las columnas dir_*; no se edita suelta.';
COMMENT ON COLUMN usuario.dir_codigo_postal IS
    'Clave del costo de envio. Es lo primero que mira cualquier paqueteria.';

-- Las mismas reglas que en `compras`, y por el mismo motivo: esta tabla tambien
-- la tocan migraciones y scripts, y un codigo postal mal formado no se descubre
-- hasta que la paqueteria devuelve el paquete.
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_dir_codigo_postal
    CHECK (dir_codigo_postal IS NULL OR dir_codigo_postal ~ '^[0-9]{5}$');

-- O van las dos coordenadas o no va ninguna: media coordenada no ubica nada y
-- pintaria un mapa en mitad del oceano, que es peor que no tener punto porque
-- parece un dato.
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_dir_coordenadas CHECK (
    (dir_latitud IS NULL AND dir_longitud IS NULL)
    OR (dir_latitud BETWEEN -90 AND 90 AND dir_longitud BETWEEN -180 AND 180)
);
