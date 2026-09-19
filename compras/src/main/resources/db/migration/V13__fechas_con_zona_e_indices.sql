-- ============================================================================
--  Fechas con zona horaria e indices en las claves foraneas
-- ============================================================================
--
--  FECHAS. Siete columnas guardaban TIMESTAMP sin zona: la hora "de pared" que
--  tuviera la JVM al escribir. Un servidor en UTC y otro en Lima escribirian
--  cosas distintas para el mismo instante, y una comparacion con ahora() se
--  corre cinco horas. `catalogo` lo corrigio en su V17 (reserva_stock); aqui
--  faltaba. TIMESTAMPTZ guarda el instante absoluto; la zona solo se usa al
--  mostrar. Las entidades pasan de LocalDateTime a Instant.
--
--  Como en V17 de catalogo, los valores existentes se reinterpretan con la zona
--  de la sesion (UTC en los contenedores). Los datos de desarrollo escritos
--  desde un portatil en Lima quedan corridos cinco horas: son fechas de
--  creacion de pedidos de prueba, no afectan a nada que se calcule.
ALTER TABLE carrito            ALTER COLUMN creado_en              TYPE TIMESTAMP WITH TIME ZONE;
ALTER TABLE clave_idempotencia ALTER COLUMN creado_en              TYPE TIMESTAMP WITH TIME ZONE;
ALTER TABLE pedido             ALTER COLUMN creado_en              TYPE TIMESTAMP WITH TIME ZONE;
ALTER TABLE saga_checkout      ALTER COLUMN creado_en              TYPE TIMESTAMP WITH TIME ZONE,
                               ALTER COLUMN actualizado_en         TYPE TIMESTAMP WITH TIME ZONE;
ALTER TABLE envios             ALTER COLUMN fecha_envio_programado TYPE TIMESTAMP WITH TIME ZONE,
                               ALTER COLUMN fecha_envio_entregado  TYPE TIMESTAMP WITH TIME ZONE;

--  INDICES. Postgres no indexa una clave foranea por su cuenta. Sin indice, el
--  JOIN del pedido con su metodo de pago y la busqueda de la saga por pedido
--  recorren la tabla entera; hoy no se nota, con volumen si.
CREATE INDEX IF NOT EXISTS idx_pedido_metodopago      ON pedido (metodopago_id);
CREATE INDEX IF NOT EXISTS idx_saga_checkout_pedido   ON saga_checkout (pedido_id);
