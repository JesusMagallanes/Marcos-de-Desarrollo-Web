-- Soporte de inicio de sesión con Google y Facebook.
--
-- El monolito guardaba la MISMA contraseña ("Aa@12345") para todas las cuentas
-- creadas por OAuth, así que cualquiera que supiera un correo entraba por el
-- formulario. Aquí las cuentas OAuth simplemente no tienen contraseña.

ALTER TABLE usuario
    ADD COLUMN proveedor VARCHAR(20) NOT NULL DEFAULT 'LOCAL';

ALTER TABLE usuario
    ADD CONSTRAINT ck_usuario_proveedor CHECK (proveedor IN ('LOCAL', 'GOOGLE', 'FACEBOOK'));

-- Una cuenta OAuth no tiene hash: password_hash pasa a admitir NULL.
ALTER TABLE usuario
    ALTER COLUMN password_hash DROP NOT NULL;

-- Coherencia: si es LOCAL debe tener contraseña; si es OAuth, no.
ALTER TABLE usuario
    ADD CONSTRAINT ck_usuario_password_local
        CHECK ((proveedor = 'LOCAL' AND password_hash IS NOT NULL)
            OR (proveedor <> 'LOCAL'));

-- El teléfono y la dirección no los da el proveedor OAuth; se completan luego
-- desde "Mi cuenta", así que dejan de ser obligatorios a nivel de columna.
ALTER TABLE usuario ALTER COLUMN phone_number DROP NOT NULL;
ALTER TABLE usuario ALTER COLUMN address DROP NOT NULL;
