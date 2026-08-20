-- Un pedido en firme que todavía no se ha cobrado: el pago contra entrega.
--
-- Hasta ahora los cinco estados daban por hecho que se cobraba ANTES de
-- entregar, porque el único checkout que existía era el de la pasarela. En el
-- pago contra entrega el dinero llega al final, con el repartidor, y ninguno de
-- los estados servía:
--
--   PENDIENTE -> es un checkout empezado y abandonado. No sale en «Mis compras»
--                a propósito, así que el comprador no vería el pedido que acaba
--                de hacer; y la saga lo cancela al no llegar el pago.
--   PAGADO    -> mentira: no se ha cobrado nada. Y ese es el estado que habilita
--                valorar el producto, así que se podría opinar de algo que ni se
--                ha recibido ni se ha pagado.
--
-- CONFIRMADO es lo que faltaba: el pedido existe, el stock ya salió del
-- inventario y hay que llevarlo, pero el dinero no ha entrado. De ahí pasa a
-- EN_TRANSITO como cualquier otro, y al entregarse se cobra.
--
-- Solo DDL: no hace falta `SET LOCAL app.omitir_rls` porque no se toca ninguna
-- fila (ver el aviso de V4__row_level_security.sql).

ALTER TABLE pedido DROP CONSTRAINT ck_pedido_estado;

ALTER TABLE pedido ADD CONSTRAINT ck_pedido_estado
    CHECK (estado IN ('PENDIENTE', 'CONFIRMADO', 'PAGADO', 'EN_TRANSITO', 'ENTREGADO', 'CANCELADO'));
