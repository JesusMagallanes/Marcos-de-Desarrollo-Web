-- ============================================================================
--  Tope de gasto en el proveedor de IA, compartido por todas las replicas
-- ============================================================================
--
--  POR QUE EN LA BASE Y NO EN MEMORIA
--
--  El tope vivia en un contador del proceso. Con una sola instancia funciona,
--  pero con tres replicas el tope efectivo es el triple: cada una cree que le
--  corresponde el cupo entero. Para CPU eso da igual —cada replica acota lo
--  que ella misma atiende—, pero aqui lo que se agota es una factura, que es
--  una sola para todas.
--
--  POR QUE NO REDIS
--
--  Redis seria otro servicio que desplegar, vigilar y del que depender, para
--  un contador que cambia como mucho 120 veces por minuto. La base ya esta,
--  ya es transaccional y el viaje (unos milisegundos) es ruido al lado de la
--  llamada al modelo, que tarda segundos. Si algun dia el cupo por IP tuviera
--  que ser global —eso si serian miles de escrituras por minuto— la respuesta
--  seria otra; ver el comentario de `LimitadorPeticiones`.
--
--  UNA FILA, SIEMPRE LA MISMA
--
--  `clave` es el ambito del tope ('global') y no crece. `minuto` es el minuto
--  epoch en curso: al cambiar, el contador se reinicia dentro del mismo UPSERT,
--  sin borrados ni tarea programada. Ver `PresupuestoIa`.
CREATE TABLE ia_presupuesto (
    clave  VARCHAR(40) PRIMARY KEY,
    minuto BIGINT  NOT NULL,
    usadas INTEGER NOT NULL
);

-- Sin RLS: aqui no hay ningun dato de usuario, solo una cuenta de llamadas.
