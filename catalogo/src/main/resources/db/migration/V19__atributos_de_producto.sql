-- Etapa 1 del plan de docs/modelo-datos.md: las especificaciones dejan de ser
-- un bloque de texto.
--
-- ═══════════════ La violación de 1FN que se corrige ═══════════════
--
-- `producto.specifications` es un TEXT con una lista dentro:
--
--     · **Pantalla**: 27 pulgadas
--     · **Panel**: IPS
--     · **Refresco**: 144 Hz
--
-- Es una tabla entera metida en una celda. La primera forma normal pide valores
-- atómicos, y esto son N pares característica/valor concatenados con formato.
--
-- Lo que cuesta, en concreto:
--
--   · No se puede FILTRAR. «Monitores de 27 pulgadas» obliga a un LIKE sobre
--     texto que también encuentra «2700 MHz». Los filtros por facetas son la
--     forma normal de navegar un catálogo grande, y con esto no existen.
--   · No se puede COMPARAR dos productos característica a característica.
--   · No hay VOCABULARIO: un producto dice «Pantalla», otro «Tamaño de
--     pantalla» y un tercero «Display». Son lo mismo y nada lo sabe.
--   · No hay TIPOS NI UNIDADES: «144», «144Hz» y «144 Hz» conviven.
--
-- ═══════════════ 1 · El vocabulario ═══════════════

CREATE TABLE atributo (
    id       BIGSERIAL PRIMARY KEY,
    -- La clave natural, en minúsculas y sin espacios: 'pulgadas', 'ram_gb'.
    -- Es lo que permite que dos productos hablen de lo mismo.
    codigo   VARCHAR(60)  NOT NULL,
    nombre   VARCHAR(100) NOT NULL,
    -- Separada del valor a propósito: guardar «144 Hz» devolvería el problema
    -- de origen. El número se ordena y se filtra; la unidad solo se enseña.
    unidad   VARCHAR(20),
    tipo     VARCHAR(20)  NOT NULL DEFAULT 'TEXTO',
    -- Si vale la pena ofrecerlo como filtro en el listado de la categoría.
    filtrable BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_atributo_codigo UNIQUE (codigo),
    CONSTRAINT ck_atributo_tipo CHECK (tipo IN ('TEXTO', 'NUMERO', 'BOOLEANO'))
);

COMMENT ON COLUMN atributo.codigo IS
    'Clave natural estable. Es lo que hace comparables dos productos.';

-- ═══════════════ 2 · El valor para cada producto ═══════════════
--
-- Clave (producto_id, atributo_id): un producto tiene UN valor de cada
-- característica. Que no se pueda repetir no es un detalle, es la garantía de
-- que no aparezcan dos «RAM» distintas en la misma ficha.

CREATE TABLE producto_atributo (
    producto_id  BIGINT       NOT NULL,
    atributo_id  BIGINT       NOT NULL,
    valor        VARCHAR(300) NOT NULL,
    -- Copia numérica del valor cuando el atributo es NUMERO. Existe para poder
    -- ordenar y comparar por rango sin castear texto en cada consulta; se
    -- rellena solo si el tipo lo permite.
    valor_numero NUMERIC(14, 3),
    -- En qué orden se pintan en la ficha. Sin esto salen por id, que no
    -- significa nada para quien lee.
    posicion     INTEGER      NOT NULL DEFAULT 0,
    PRIMARY KEY (producto_id, atributo_id),
    CONSTRAINT fk_producto_atributo_producto FOREIGN KEY (producto_id)
        REFERENCES producto (id) ON DELETE CASCADE,
    CONSTRAINT fk_producto_atributo_atributo FOREIGN KEY (atributo_id)
        REFERENCES atributo (id),
    CONSTRAINT ck_producto_atributo_posicion CHECK (posicion >= 0)
);

-- El filtro por facetas pregunta «qué productos tienen este atributo con este
-- valor», así que el índice va por el lado del atributo.
CREATE INDEX idx_producto_atributo_atributo ON producto_atributo (atributo_id, valor);
CREATE INDEX idx_producto_atributo_numero ON producto_atributo (atributo_id, valor_numero)
    WHERE valor_numero IS NOT NULL;

-- ═══════════════ 3 · Qué pasa con `specifications` ═══════════════
--
-- La columna SE QUEDA, de momento, y pasa a ser un valor DERIVADO: el servicio
-- la compone a partir de las filas de `producto_atributo` para que la ficha del
-- frontend y la app móvil sigan recibiendo el mismo campo que hoy.
--
-- Es una transición deliberada en dos pasos. El almacenamiento ya está
-- normalizado; retirar la columna cambia el contrato REST y eso arrastra
-- Angular y Flutter, así que va en la etapa 3 y no aquí.

COMMENT ON COLUMN producto.specifications IS
    'DERIVADA de producto_atributo. No se edita suelta; se recompone al guardar '
    'los atributos. Se retirará en la etapa 3 (ver docs/modelo-datos.md).';
