-- Guías de ayuda ("Aprende con nosotros" en el pie).
--
-- Sustituyen a los enlaces vacíos que había en el pie. El contenido lo escribe
-- el administrador desde el panel: nada queda escrito a fuego en el HTML, que
-- era el problema a resolver.
--
-- Viven en `catalogo` y no en un servicio nuevo porque es contenido público de
-- lectura, igual que las categorías o el chatbot, y montar un quinto servicio
-- para dos tablas de texto no compensa el coste de operarlo.

CREATE TABLE guia (
    id             BIGSERIAL PRIMARY KEY,
    -- Va en la URL (/guias/como-comprar-online), así que es la clave que ve el
    -- usuario y debe ser única.
    slug           VARCHAR(140) NOT NULL,
    titulo         VARCHAR(160) NOT NULL,
    -- Frase corta para la tarjeta del listado.
    resumen        VARCHAR(300) NOT NULL,
    -- Nombre de icono de FontAwesome (sin el prefijo), para la tarjeta.
    icono          VARCHAR(60),
    -- Orden de aparición; a igualdad, se ordena por título.
    posicion       INTEGER NOT NULL DEFAULT 0,
    -- Una guía sin publicar solo la ve el administrador.
    publicada      BOOLEAN NOT NULL DEFAULT FALSE,
    creado_en      TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_guia_slug UNIQUE (slug),
    CONSTRAINT ck_guia_slug CHECK (slug ~ '^[a-z0-9-]+$')
);

CREATE INDEX idx_guia_publicada ON guia (publicada, posicion);

-- Los pasos de cada guía, en tabla aparte y no en un TEXT con formato: así el
-- panel los edita uno a uno y la tienda los pinta numerados sin interpretar
-- ningún lenguaje de marcado, que sería una vía de inyección de HTML.
CREATE TABLE guia_paso (
    id          BIGSERIAL PRIMARY KEY,
    guia_id     BIGINT       NOT NULL,
    posicion    INTEGER      NOT NULL,
    titulo      VARCHAR(160) NOT NULL,
    descripcion TEXT         NOT NULL,
    CONSTRAINT fk_guia_paso_guia FOREIGN KEY (guia_id) REFERENCES guia (id) ON DELETE CASCADE,
    CONSTRAINT uk_guia_paso_posicion UNIQUE (guia_id, posicion),
    CONSTRAINT ck_guia_paso_posicion CHECK (posicion >= 0)
);

CREATE INDEX idx_guia_paso_guia ON guia_paso (guia_id);

-- Las dos guías que el pie ya prometía y no llevaban a ninguna parte. Se
-- siembran publicadas para que la sección no nazca vacía; el administrador
-- puede reescribirlas o borrarlas desde el panel.
INSERT INTO guia (slug, titulo, resumen, icono, posicion, publicada) VALUES
    ('como-comprar-online',
     '¿Cómo comprar online?',
     'Del carrito al pago, paso a paso: elegir el producto, revisar el pedido y completar la compra.',
     'cart-shopping', 1, TRUE),
    ('como-registrarse',
     '¿Cómo registrarse en nuestra web?',
     'Crea tu cuenta en un minuto para seguir tus pedidos y comprar más rápido la próxima vez.',
     'user-plus', 2, TRUE);

INSERT INTO guia_paso (guia_id, posicion, titulo, descripcion)
SELECT g.id, p.posicion, p.titulo, p.descripcion
FROM guia g
JOIN (VALUES
    ('como-comprar-online', 0, 'Busca lo que necesitas',
     'Usa el buscador de la parte superior o entra por categorías desde el menú. En cada producto verás el precio, el stock disponible y las valoraciones de otros compradores.'),
    ('como-comprar-online', 1, 'Añádelo al carrito',
     'Pulsa "Agregar al carrito" en la ficha del producto. Puedes seguir navegando y añadir más productos: el carrito guarda todo hasta que decidas pagar.'),
    ('como-comprar-online', 2, 'Revisa tu pedido',
     'Abre el carrito con el icono de la cabecera. Ahí puedes cambiar cantidades o quitar lo que ya no quieras, y ver el total antes de continuar.'),
    ('como-comprar-online', 3, 'Elige cómo pagar',
     'Pulsa "Comprar" y escoge el método de pago. Puedes pagar con tarjeta, Yape o Plin a través de MercadoPago, o elegir pago contra entrega en Lima Metropolitana.'),
    ('como-comprar-online', 4, 'Sigue tu compra',
     'Al confirmar recibirás tu pedido en "Mis compras", dentro de tu perfil. Desde ahí puedes ver el estado del envío en todo momento.'),
    ('como-registrarse', 0, 'Abre el formulario de registro',
     'Pulsa "Iniciar sesión" en la parte superior derecha y luego "Crear cuenta". También puedes entrar directamente con tu cuenta de Google o Facebook.'),
    ('como-registrarse', 1, 'Completa tus datos',
     'Necesitamos tu nombre, apellidos, correo, teléfono y dirección de envío. El correo será tu usuario para entrar, así que asegúrate de escribirlo bien.'),
    ('como-registrarse', 2, 'Elige una contraseña segura',
     'Debe tener al menos 8 caracteres e incluir una mayúscula, una minúscula, un número y un símbolo. Es lo que protege tu cuenta y tus pedidos.'),
    ('como-registrarse', 3, 'Empieza a comprar',
     'Con la cuenta creada ya entras automáticamente. Tus datos de envío quedan guardados, así que la próxima compra te llevará mucho menos tiempo.')
) AS p(slug, posicion, titulo, descripcion) ON p.slug = g.slug;
