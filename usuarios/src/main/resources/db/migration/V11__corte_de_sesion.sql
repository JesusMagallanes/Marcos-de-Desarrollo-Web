-- Cortar TODAS las sesiones de una cuenta de golpe.
--
-- Hasta ahora la revocación era por token: una fila en `token_revocado` con su
-- `jti`. Eso vale para un logout, pero se queda corto justo en el caso que más
-- importa, el reúso de un refresh token.
--
-- Qué pasaba: si un refresh se filtra, el atacante lo canjea y recibe un par
-- nuevo. Cuando el usuario legítimo intenta canjear el suyo —ya revocado por la
-- rotación— se detecta el reúso y se le responde 401. Correcto por fuera, pero
-- el que se queda fuera es el legítimo: el token que el atacante acaba de
-- recibir no está revocado y le sirve para seguir renovando otros siete días.
--
-- Con esta marca, detectar un reúso invalida todo lo emitido hasta ese
-- instante, incluida la cadena del atacante. La comparación es contra el `iat`
-- del token, así que no hace falta conocer los `jti`.
--
-- NULL significa "sin corte", que es lo correcto para las cuentas que ya
-- existen: nunca se les ha invalidado nada.
--
-- Solo DDL: no toca ninguna fila, así que no hace falta `SET LOCAL
-- app.omitir_rls` (ver el aviso de V7__row_level_security.sql).

ALTER TABLE usuario ADD COLUMN tokens_validos_desde TIMESTAMP;

COMMENT ON COLUMN usuario.tokens_validos_desde IS
    'Instante a partir del cual valen los tokens de esta cuenta. Todo lo emitido antes se rechaza. NULL = sin corte.';
