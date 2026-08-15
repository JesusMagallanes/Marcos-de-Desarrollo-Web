-- La dirección de envío se pide de verdad.
--
-- Hasta ahora `envio.direccion` se rellenaba con el texto literal
-- 'Por confirmar' (ver EnvioService), porque nadie la preguntaba en ningún
-- momento del checkout. El resultado era que **cada pedido pagado era un pedido
-- que no se podía entregar**: había cobro, había stock descontado y no había
-- a dónde mandarlo.
--
-- Se pide al INICIAR el checkout y no al confirmarlo, por dos motivos: el
-- comprador todavía está en la tienda (después se va a MercadoPago y puede no
-- volver), y así el importe y el destino quedan fijados juntos antes de pagar.

-- ── La saga la lleva desde el inicio hasta que crea el envío ────────────────

ALTER TABLE saga_checkout ADD COLUMN direccion_envio VARCHAR(200);
ALTER TABLE saga_checkout ADD COLUMN referencia_envio VARCHAR(200);
ALTER TABLE saga_checkout ADD COLUMN telefono_contacto VARCHAR(9);

COMMENT ON COLUMN saga_checkout.direccion_envio IS
    'Destino elegido al iniciar el checkout. Se copia al envío al confirmarse el pago.';

-- ── El envío gana los datos que le faltaban ────────────────────────────────

ALTER TABLE envios ADD COLUMN referencia VARCHAR(200);
ALTER TABLE envios ADD COLUMN telefono_contacto VARCHAR(9);

-- Los envíos que ya existen se quedan con su 'Por confirmar': son anteriores a
-- que se pidiera la dirección y no hay forma honesta de inventarles un destino.
-- Se marcan para que quien gestione envíos sepa que a esos hay que llamarles.
--
-- SIN LA LÍNEA DE ABAJO ESTE UPDATE TOCARÍA CERO FILAS Y NADIE SE ENTERARÍA.
-- `envios` tiene FORCE ROW LEVEL SECURITY desde la V4, y eso alcanza también al
-- dueño de las tablas, que es con quien corre Flyway. Sin contexto de usuario
-- las políticas no dejan ver ninguna fila, el UPDATE afecta a 0 registros y la
-- migración se da por aplicada con éxito. Está avisado en la cabecera de la V4.
SET LOCAL app.omitir_rls = 'on';

UPDATE envios SET referencia = 'Dirección no registrada: contactar al comprador'
WHERE direccion = 'Por confirmar';
