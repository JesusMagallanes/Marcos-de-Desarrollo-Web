-- Etapa 1 del plan de docs/modelo-datos.md: la taxonomía y las marcas.
--
-- Dos cambios que van juntos porque los dos hablan de "dónde encaja" un
-- producto, y el segundo corrige una violación de 4FN.

-- ═══════════════ 1 · Categorías con jerarquía ═══════════════
--
-- Hasta ahora la taxonomía era plana: una lista de categorías hermanas. Un
-- catálogo de verdad es un árbol —«Tecnología › Computación › Laptops»— y sin
-- él no hay migas de pan, ni menú desplegable, ni «ver todo lo de Computación»
-- incluyendo lo que cuelga por debajo.
--
-- Se modela con una autorreferencia y no con una tabla aparte: es la misma
-- entidad en los dos extremos. NULL = categoría raíz.

ALTER TABLE categoria ADD COLUMN categoria_padre_id BIGINT;

ALTER TABLE categoria ADD CONSTRAINT fk_categoria_padre
    FOREIGN KEY (categoria_padre_id) REFERENCES categoria (id);

-- Un ciclo de un solo nodo es el error fácil de cometer desde el panel y deja
-- el árbol irrecorrible. Los ciclos largos no se pueden atajar con un CHECK;
-- de esos se encarga el servicio al guardar.
ALTER TABLE categoria ADD CONSTRAINT ck_categoria_no_es_su_padre
    CHECK (categoria_padre_id IS NULL OR categoria_padre_id <> id);

CREATE INDEX idx_categoria_padre ON categoria (categoria_padre_id);

COMMENT ON COLUMN categoria.categoria_padre_id IS
    'Categoría que la contiene. NULL = raíz del árbol.';

-- ═══════════════ 2 · Marca ↔ categoría: de 1:N a M:N ═══════════════
--
-- VIOLACIÓN DE 4FN QUE SE CORRIGE AQUÍ.
--
-- `marca.categoria_id` obliga a que una marca pertenezca a UNA categoría. Es
-- falso en cualquier tienda: LG vende monitores, televisores y refrigeradoras.
-- Para representarlo con el modelo viejo habría que dar de alta «LG», «LG (TV)»
-- y «LG (Línea blanca)» como marcas distintas, y `uk_marca_name` lo impide.
--
-- Formalmente: en el universo (marca, categoría, producto) se cumple
-- `marca ↠ categoría` con independencia de los productos concretos. Una
-- dependencia multivaluada que no está proyectada en su propia tabla es
-- exactamente lo que la cuarta forma normal prohíbe.

CREATE TABLE marca_categoria (
    marca_id     BIGINT NOT NULL,
    categoria_id BIGINT NOT NULL,
    PRIMARY KEY (marca_id, categoria_id),
    CONSTRAINT fk_marca_categoria_marca FOREIGN KEY (marca_id)
        REFERENCES marca (id) ON DELETE CASCADE,
    CONSTRAINT fk_marca_categoria_categoria FOREIGN KEY (categoria_id)
        REFERENCES categoria (id) ON DELETE CASCADE
);

-- La consulta que existe hoy es «marcas de esta categoría», así que el índice
-- que hace falta es el del lado contrario a la clave primaria.
CREATE INDEX idx_marca_categoria_categoria ON marca_categoria (categoria_id);

-- Se conserva lo que hubiera: cada marca entra en la categoría que tenía.
INSERT INTO marca_categoria (marca_id, categoria_id)
SELECT id, categoria_id FROM marca;

-- Y solo entonces se retira la columna. El orden importa: al revés se pierde
-- el dato antes de copiarlo.
ALTER TABLE marca DROP COLUMN categoria_id;
