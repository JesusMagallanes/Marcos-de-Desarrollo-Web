-- Punto en el mapa del domicilio de la solicitud.
--
-- La direccion escrita dice a donde ir; el punto dice si ese sitio existe y
-- esta donde el solicitante afirma. Quien revisa necesita las dos cosas: un
-- texto solo no se puede comprobar sin salir de la pantalla.
--
-- Ambas son NULL-ables a proposito. No todo el mundo vende desde un local, y
-- exigir el permiso de ubicacion para poder enviar la solicitud dejaria fuera a
-- quien entra desde un ordenador sin GPS o simplemente dice que no.

ALTER TABLE solicitud_colaborador
    ADD COLUMN latitud  DECIMAL(9, 6),
    ADD COLUMN longitud DECIMAL(9, 6);

-- Las dos van juntas o no va ninguna: media coordenada no ubica nada, y sin
-- esta regla una fila a medias pintaria un mapa en el centro del oceano.
--
-- Los rangos tambien se comprueban aqui y no solo en la validacion de Java:
-- esta tabla la puede tocar una migracion o un script, y una latitud de 200
-- solo se detecta cuando alguien abre el mapa y ve el vacio.
ALTER TABLE solicitud_colaborador
    ADD CONSTRAINT ck_solicitud_ubicacion_completa
        CHECK ((latitud IS NULL) = (longitud IS NULL)),
    ADD CONSTRAINT ck_solicitud_latitud
        CHECK (latitud IS NULL OR latitud BETWEEN -90 AND 90),
    ADD CONSTRAINT ck_solicitud_longitud
        CHECK (longitud IS NULL OR longitud BETWEEN -180 AND 180);

COMMENT ON COLUMN solicitud_colaborador.latitud IS
    'Punto del domicilio, opcional. DECIMAL(9,6) ~ 11 cm, la precision que da un navegador.';
