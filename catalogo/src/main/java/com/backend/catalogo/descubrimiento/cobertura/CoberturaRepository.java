package com.backend.catalogo.descubrimiento.cobertura;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.backend.catalogo.descubrimiento.RecomendacionServida;

/**
 * Cuánto catálogo toca el recomendador, preguntado al revés.
 *
 * <h4>Por qué se parte del universo y no de lo expuesto</h4>
 *
 * <p>La pregunta que importa no es «qué categorías aparecieron» sino «cuáles NO
 * aparecieron», y esas dos no se responden con la misma consulta. Un
 * {@code SELECT DISTINCT categoria FROM recomendacion_servida} solo puede
 * devolver las que salieron: las que el recomendador ignora durante un mes son
 * exactamente las filas que esa consulta nunca produce. Por eso todo lo de aquí
 * arranca del catálogo elegible y hace {@code LEFT JOIN} contra la exposición.
 *
 * <h4>Servido y visto son la misma pregunta sobre dos tablas</h4>
 *
 * <p>{@code recomendacion_servida} es lo que el backend decidió enseñar;
 * {@code impresion} es lo que el navegador confirmó que entró en pantalla. La
 * fase 3 las separó a propósito y aquí no se mezclan: se unen con una etiqueta
 * {@code fuente} y se agrupan por ella, de modo que una sola consulta devuelve
 * las dos columnas de la respuesta sin confundirlas nunca en la misma cifra.
 *
 * <h4>Qué es elegible</h4>
 *
 * <p>{@code estado_moderacion = 'APROBADO' AND stock > 0}, que es literalmente
 * la condición de {@code ProductoElegibleRepository} y la que aplican los
 * generadores en su propio SQL. No hay una lista de filtros distinta para las
 * métricas: medir con una definición y servir con otra produce un panel que
 * miente con precisión.
 */
public interface CoberturaRepository extends JpaRepository<RecomendacionServida, Long> {

    /**
     * Una fila por (fuente, categoría) de TODAS las categorías con catálogo vivo.
     *
     * <p>Las categorías sin un solo producto elegible no salen, y es lo
     * correcto: no son un olvido del recomendador, es que no hay nada que
     * recomendar en ellas. Meterlas hundiría la cobertura con un problema de
     * catálogo disfrazado de problema de recomendación.
     *
     * <p>{@code top_producto} es el máximo de exposiciones de un solo producto
     * dentro de la categoría. Con él se responde si una categoría concentra por
     * amplitud o porque dos productos se lo llevan todo, sin persistir en ningún
     * sitio qué producto era.
     *
     * @param modulo un módulo concreto, o {@code null} para todos
     * @return filas {@code [fuente, categoriaId, nombre, productosElegibles,
     *     productosExpuestos, exposiciones, topProducto]}
     */
    @Query(value = """
            WITH elegible AS (
                SELECT p.id, p.categoria_id, p.marca_id
                  FROM catalogo.producto p
                 WHERE p.estado_moderacion = 'APROBADO' AND p.stock > 0
            ),
            exposicion AS (
                SELECT CAST('SERVIDO' AS text) AS fuente, r.item_id
                  FROM catalogo.recomendacion_servida r
                 WHERE r.item_tipo = 'PRODUCTO'
                   AND r.servido_en >= :desde AND r.servido_en < :hasta
                   AND (CAST(:modulo AS text) IS NULL OR r.modulo = CAST(:modulo AS text))
                 UNION ALL
                SELECT CAST('VISTO' AS text), i.item_id
                  FROM catalogo.impresion i
                 WHERE i.item_tipo = 'PRODUCTO'
                   AND i.mostrado_en >= :desde AND i.mostrado_en < :hasta
                   AND (CAST(:modulo AS text) IS NULL OR i.modulo = CAST(:modulo AS text))
            ),
            /* Nivel producto: hace falta para poder mirar dentro de la categoria. */
            por_producto AS (
                SELECT x.fuente, e.categoria_id, x.item_id, COUNT(*) AS veces
                  FROM exposicion x
                  JOIN elegible e ON e.id = x.item_id
                 GROUP BY 1, 2, 3
            ),
            por_categoria AS (
                SELECT fuente, categoria_id,
                       COUNT(*)   AS productos_expuestos,
                       SUM(veces) AS exposiciones,
                       MAX(veces) AS top_producto
                  FROM por_producto
                 GROUP BY 1, 2
            ),
            /* El universo: cada fuente por cada categoria con catalogo vivo. */
            universo AS (
                SELECT f.fuente, e.categoria_id, COUNT(*) AS productos_elegibles
                  FROM (SELECT CAST('SERVIDO' AS text) AS fuente
                         UNION ALL SELECT CAST('VISTO' AS text)) f
                  CROSS JOIN elegible e
                 GROUP BY 1, 2
            )
            SELECT u.fuente, u.categoria_id, c.name, u.productos_elegibles,
                   COALESCE(p.productos_expuestos, 0),
                   COALESCE(p.exposiciones, 0),
                   COALESCE(p.top_producto, 0)
              FROM universo u
              JOIN catalogo.categoria c ON c.id = u.categoria_id
              LEFT JOIN por_categoria p
                     ON p.fuente = u.fuente AND p.categoria_id = u.categoria_id
             ORDER BY u.fuente, 6 DESC, u.categoria_id
            """, nativeQuery = true)
    List<Object[]> porCategoria(@Param("desde") Instant desde, @Param("hasta") Instant hasta,
            @Param("modulo") String modulo);

    /**
     * Lo mismo por marca, con una diferencia que importa.
     *
     * <p>{@code producto.marca_id} admite nulo, así que hay productos elegibles
     * que no pertenecen a ninguna marca. Esos no aparecen aquí —no se les puede
     * atribuir— y por eso el total de productos NO se calcula desde esta
     * consulta sino desde la de categorías, donde la columna es obligatoria y
     * cada producto cuenta exactamente una vez.
     *
     * @return filas {@code [fuente, marcaId, nombre, productosElegibles,
     *     productosExpuestos, exposiciones, topProducto]}
     */
    @Query(value = """
            WITH elegible AS (
                SELECT p.id, p.marca_id
                  FROM catalogo.producto p
                 WHERE p.estado_moderacion = 'APROBADO' AND p.stock > 0
                   AND p.marca_id IS NOT NULL
            ),
            exposicion AS (
                SELECT CAST('SERVIDO' AS text) AS fuente, r.item_id
                  FROM catalogo.recomendacion_servida r
                 WHERE r.item_tipo = 'PRODUCTO'
                   AND r.servido_en >= :desde AND r.servido_en < :hasta
                   AND (CAST(:modulo AS text) IS NULL OR r.modulo = CAST(:modulo AS text))
                 UNION ALL
                SELECT CAST('VISTO' AS text), i.item_id
                  FROM catalogo.impresion i
                 WHERE i.item_tipo = 'PRODUCTO'
                   AND i.mostrado_en >= :desde AND i.mostrado_en < :hasta
                   AND (CAST(:modulo AS text) IS NULL OR i.modulo = CAST(:modulo AS text))
            ),
            por_producto AS (
                SELECT x.fuente, e.marca_id, x.item_id, COUNT(*) AS veces
                  FROM exposicion x
                  JOIN elegible e ON e.id = x.item_id
                 GROUP BY 1, 2, 3
            ),
            por_marca AS (
                SELECT fuente, marca_id,
                       COUNT(*)   AS productos_expuestos,
                       SUM(veces) AS exposiciones,
                       MAX(veces) AS top_producto
                  FROM por_producto
                 GROUP BY 1, 2
            ),
            universo AS (
                SELECT f.fuente, e.marca_id, COUNT(*) AS productos_elegibles
                  FROM (SELECT CAST('SERVIDO' AS text) AS fuente
                         UNION ALL SELECT CAST('VISTO' AS text)) f
                  CROSS JOIN elegible e
                 GROUP BY 1, 2
            )
            SELECT u.fuente, u.marca_id, m.name, u.productos_elegibles,
                   COALESCE(p.productos_expuestos, 0),
                   COALESCE(p.exposiciones, 0),
                   COALESCE(p.top_producto, 0)
              FROM universo u
              JOIN catalogo.marca m ON m.id = u.marca_id
              LEFT JOIN por_marca p ON p.fuente = u.fuente AND p.marca_id = u.marca_id
             ORDER BY u.fuente, 6 DESC, u.marca_id
            """, nativeQuery = true)
    List<Object[]> porMarca(@Param("desde") Instant desde, @Param("hasta") Instant hasta,
            @Param("modulo") String modulo);

    /**
     * La concentración partida por banda de posición.
     *
     * <p>La banda es {@code LEAST(posicion / 3, 7)}, exactamente la misma
     * expresión que ya usa la agregación diaria de la fase 3. Se repite en vez
     * de inventar otra clasificación porque dos criterios de posición conviviendo
     * harían incomparables los números de un panel con los del otro.
     *
     * <p>Hace falta porque una exposición en el puesto 1 y otra en el 12 no son
     * la misma cosa: un recomendador puede parecer variado mirando el total y
     * estar enseñando siempre las mismas tres categorías arriba del todo, que es
     * donde la gente mira.
     *
     * @return filas {@code [fuente, banda, exposiciones, categorias,
     *     productos, topCategoria]}
     */
    @Query(value = """
            WITH elegible AS (
                SELECT p.id, p.categoria_id
                  FROM catalogo.producto p
                 WHERE p.estado_moderacion = 'APROBADO' AND p.stock > 0
            ),
            exposicion AS (
                SELECT CAST('SERVIDO' AS text) AS fuente, r.item_id,
                       LEAST(r.posicion / 3, 7)::smallint AS banda
                  FROM catalogo.recomendacion_servida r
                 WHERE r.item_tipo = 'PRODUCTO'
                   AND r.servido_en >= :desde AND r.servido_en < :hasta
                   AND (CAST(:modulo AS text) IS NULL OR r.modulo = CAST(:modulo AS text))
                 UNION ALL
                SELECT CAST('VISTO' AS text), i.item_id,
                       LEAST(COALESCE(i.posicion, 0) / 3, 7)::smallint
                  FROM catalogo.impresion i
                 WHERE i.item_tipo = 'PRODUCTO'
                   AND i.mostrado_en >= :desde AND i.mostrado_en < :hasta
                   AND (CAST(:modulo AS text) IS NULL OR i.modulo = CAST(:modulo AS text))
            ),
            por_categoria AS (
                SELECT x.fuente, x.banda, e.categoria_id,
                       COUNT(*) AS veces,
                       COUNT(DISTINCT x.item_id) AS productos
                  FROM exposicion x
                  JOIN elegible e ON e.id = x.item_id
                 GROUP BY 1, 2, 3
            )
            SELECT fuente, banda,
                   SUM(veces), COUNT(*), SUM(productos), MAX(veces)
              FROM por_categoria
             GROUP BY 1, 2
             ORDER BY 1, 2
            """, nativeQuery = true)
    List<Object[]> porBanda(@Param("desde") Instant desde, @Param("hasta") Instant hasta,
            @Param("modulo") String modulo);
}
