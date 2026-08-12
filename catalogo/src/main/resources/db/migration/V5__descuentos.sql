-- Descuentos sobre el precio de venta.
--
-- `precio` sigue siendo el precio de lista (el "antes"). Cuando un producto
-- está en oferta, `precio_oferta` guarda el precio resultante ya calculado y
-- `descuento_tipo` + `descuento_valor` recuerdan cómo se llegó a él (para
-- poder mostrarlo y volver a editar el descuento). Las fechas definen la
-- vigencia: fuera de ese rango el cliente paga `precio` de nuevo.

ALTER TABLE producto
    ADD COLUMN precio_oferta   NUMERIC(12, 2),
    ADD COLUMN descuento_tipo  VARCHAR(20),
    ADD COLUMN descuento_valor NUMERIC(12, 2),
    ADD COLUMN oferta_inicio   TIMESTAMP,
    ADD COLUMN oferta_fin      TIMESTAMP;

ALTER TABLE producto
    ADD CONSTRAINT ck_producto_precio_oferta CHECK (precio_oferta IS NULL OR precio_oferta >= 0),
    ADD CONSTRAINT ck_producto_descuento_tipo CHECK (
        descuento_tipo IS NULL OR descuento_tipo IN ('PORCENTAJE', 'MONTO')
    ),
    ADD CONSTRAINT ck_producto_descuento_valor CHECK (
        descuento_valor IS NULL OR descuento_valor >= 0
    ),
    ADD CONSTRAINT ck_producto_oferta_fechas CHECK (
        oferta_fin IS NULL OR oferta_inicio IS NULL OR oferta_fin >= oferta_inicio
    );
