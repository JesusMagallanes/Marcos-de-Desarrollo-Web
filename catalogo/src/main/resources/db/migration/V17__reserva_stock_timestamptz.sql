-- Las columnas de tiempo en reserva_stock usaban TIMESTAMP (sin zona), lo que
-- hacia que la comparacion con Instant.now() (UTC) pudiera fallar si la zona
-- del servidor o la sesion de Postgres no era UTC.
--
-- TIMESTAMPTZ almacena el instante absoluto: la zona solo se usa al mostrar,
-- nunca al comparar. Los valores existentes se reinterpretan con la zona de la
-- sesion actual (UTC en produccion).

ALTER TABLE reserva_stock
    ALTER COLUMN expira_en TYPE TIMESTAMP WITH TIME ZONE,
    ALTER COLUMN creado_en TYPE TIMESTAMP WITH TIME ZONE;
