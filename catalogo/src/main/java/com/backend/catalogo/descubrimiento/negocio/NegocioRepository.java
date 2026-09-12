package com.backend.catalogo.descubrimiento.negocio;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.backend.catalogo.descubrimiento.RecomendacionServida;

/**
 * El embudo de negocio, por categoría, con la atribución que de verdad hay.
 *
 * <h4>Qué atribución hay, exactamente</h4>
 *
 * <p>Por VENTANA TEMPORAL, y nada más. Una acción cuenta para una recomendación
 * si la hizo el mismo sujeto sobre el mismo producto, DESPUÉS de servirla y
 * dentro de las horas de atribución. Es literalmente el mismo criterio que ya
 * aplica la agregación diaria de la fase 3, copiado y no reinventado, para que
 * las cifras de aquí y las de allí se puedan poner una al lado de la otra.
 *
 * <p>Lo que esto NO es: una cadena causal. {@code evento_interaccion.origen} es
 * texto libre que manda el cliente, no una referencia a la recomendación que lo
 * provocó, así que el modelo NO puede demostrar que un carrito vino de un
 * carrusel concreto. Alguien puede ver una recomendación, ignorarla, buscar el
 * producto por el buscador y comprarlo: eso aquí cuenta como asociado, y decir
 * otra cosa sería inventarse un dato que la ingesta no garantiza.
 *
 * <p>Por eso todo lo que sale de aquí se llama ASOCIADO y no «generado».
 *
 * <h4>El universo es el catálogo, no lo servido</h4>
 *
 * <p>Igual que en el bloque E: se parte de las categorías con catálogo elegible
 * y se hace {@code LEFT JOIN} contra la actividad. Una categoría que no recibe
 * exposición no produce ninguna fila en {@code recomendacion_servida}, así que
 * arrancar de ahí la haría invisible — y es justo la que hay que ver.
 */
public interface NegocioRepository extends JpaRepository<RecomendacionServida, Long> {

    /**
     * Una fila por categoría con catálogo vivo, con el embudo entero.
     *
     * <p>Las etapas se conservan todas y por separado. Un solo número resumen
     * escondería el caso que más importa detectar: mil vistas, cien clics y
     * cero carritos no es una categoría que funcione, es una que llama la
     * atención y no convierte.
     *
     * <p>{@code sujetos} es el piso de evidencia, el mismo de la fase 3: una
     * tasa sostenida por tres personas no describe un patrón, describe a esas
     * tres personas. Sirve para marcar la fila como insuficiente, no para
     * identificar a nadie.
     *
     * @param horas ventana de atribución, la que ya usa la medición diaria
     * @param modulo un carrusel concreto, o {@code null} para todos
     * @return filas {@code [categoriaId, nombre, productosElegibles, servidas,
     *     vistas, clics, profundas, carritos, compras, sujetos]}
     */
    @Query(value = """
            WITH elegible AS (
                SELECT p.id, p.categoria_id
                  FROM catalogo.producto p
                 WHERE p.estado_moderacion = 'APROBADO' AND p.stock > 0
            ),
            servida AS (
                SELECT r.id, r.sujeto_id, r.item_id, r.modulo, r.servido_en,
                       e.categoria_id
                  FROM catalogo.recomendacion_servida r
                  JOIN elegible e ON e.id = r.item_id
                 WHERE r.item_tipo = 'PRODUCTO'
                   AND r.servido_en >= :desde AND r.servido_en < :hasta
                   AND (CAST(:modulo AS text) IS NULL
                        OR r.modulo = CAST(:modulo AS text))
            ),
            /* Lo que el navegador confirmo que entro en pantalla. */
            vista AS (
                SELECT s.id
                  FROM servida s
                  JOIN catalogo.impresion i
                    ON i.sujeto_id = s.sujeto_id
                   AND i.item_id = s.item_id
                   AND i.modulo = s.modulo
                   AND i.mostrado_en >= s.servido_en
                   AND i.mostrado_en < s.servido_en + make_interval(hours => :horas)
                 GROUP BY s.id
            ),
            /* Lo que hizo DESPUES, dentro de la ventana. Asociacion, no causa. */
            accion AS (
                SELECT s.id,
                       BOOL_OR(e.tipo IN ('ITEM_VIEW', 'ITEM_VIEW_DEEP')) AS hubo_clic,
                       BOOL_OR(e.tipo = 'ITEM_VIEW_DEEP')                 AS hubo_profunda,
                       BOOL_OR(e.tipo = 'ADD_TO_CART')                    AS hubo_carrito,
                       BOOL_OR(e.tipo = 'PURCHASE')                       AS hubo_compra
                  FROM servida s
                  JOIN catalogo.evento_interaccion e
                    ON e.sujeto_id = s.sujeto_id
                   AND e.item_id = s.item_id
                   AND e.ocurrido_en >= s.servido_en
                   AND e.ocurrido_en < s.servido_en + make_interval(hours => :horas)
                 GROUP BY s.id
            ),
            embudo AS (
                SELECT s.categoria_id,
                       COUNT(*)                                        AS servidas,
                       COUNT(v.id)                                     AS vistas,
                       COUNT(*) FILTER (WHERE a.hubo_clic)             AS clics,
                       COUNT(*) FILTER (WHERE a.hubo_profunda)         AS profundas,
                       COUNT(*) FILTER (WHERE a.hubo_carrito)          AS carritos,
                       COUNT(*) FILTER (WHERE a.hubo_compra)           AS compras,
                       COUNT(DISTINCT s.sujeto_id)                     AS sujetos
                  FROM servida s
                  LEFT JOIN vista v ON v.id = s.id
                  LEFT JOIN accion a ON a.id = s.id
                 GROUP BY 1
            ),
            universo AS (
                SELECT categoria_id, COUNT(*) AS productos_elegibles
                  FROM elegible GROUP BY 1
            )
            SELECT u.categoria_id, c.name, u.productos_elegibles,
                   COALESCE(b.servidas, 0), COALESCE(b.vistas, 0),
                   COALESCE(b.clics, 0), COALESCE(b.profundas, 0),
                   COALESCE(b.carritos, 0), COALESCE(b.compras, 0),
                   COALESCE(b.sujetos, 0)
              FROM universo u
              JOIN catalogo.categoria c ON c.id = u.categoria_id
              LEFT JOIN embudo b ON b.categoria_id = u.categoria_id
             ORDER BY 4 DESC, 1
            """, nativeQuery = true)
    List<Object[]> embudoPorCategoria(@Param("desde") Instant desde,
            @Param("hasta") Instant hasta, @Param("horas") int horas,
            @Param("modulo") String modulo);

    /**
     * El mismo embudo partido por banda de posición.
     *
     * <p>La banda es {@code LEAST(posicion / 3, 7)}, la de la fase 3. Responde
     * la pregunta incómoda: si una categoría rinde o solo sale siempre arriba.
     * Una tasa de clic alta en la banda 0 y nula en la 3 no habla de la
     * categoría, habla de dónde se la coloca.
     *
     * <p>Aquí NO se parte del universo: una banda de una categoría que no
     * apareció no existe como concepto. Las categorías sin exposición se
     * responden con {@link #embudoPorCategoria}.
     *
     * @return filas {@code [categoriaId, banda, servidas, vistas, clics,
     *     profundas, carritos, compras, sujetos]}
     */
    @Query(value = """
            WITH elegible AS (
                SELECT p.id, p.categoria_id
                  FROM catalogo.producto p
                 WHERE p.estado_moderacion = 'APROBADO' AND p.stock > 0
            ),
            servida AS (
                SELECT r.id, r.sujeto_id, r.item_id, r.modulo, r.servido_en,
                       e.categoria_id,
                       LEAST(r.posicion / 3, 7)::smallint AS banda
                  FROM catalogo.recomendacion_servida r
                  JOIN elegible e ON e.id = r.item_id
                 WHERE r.item_tipo = 'PRODUCTO'
                   AND r.servido_en >= :desde AND r.servido_en < :hasta
                   AND (CAST(:modulo AS text) IS NULL
                        OR r.modulo = CAST(:modulo AS text))
            ),
            vista AS (
                SELECT s.id
                  FROM servida s
                  JOIN catalogo.impresion i
                    ON i.sujeto_id = s.sujeto_id
                   AND i.item_id = s.item_id
                   AND i.modulo = s.modulo
                   AND i.mostrado_en >= s.servido_en
                   AND i.mostrado_en < s.servido_en + make_interval(hours => :horas)
                 GROUP BY s.id
            ),
            accion AS (
                SELECT s.id,
                       BOOL_OR(e.tipo IN ('ITEM_VIEW', 'ITEM_VIEW_DEEP')) AS hubo_clic,
                       BOOL_OR(e.tipo = 'ITEM_VIEW_DEEP')                 AS hubo_profunda,
                       BOOL_OR(e.tipo = 'ADD_TO_CART')                    AS hubo_carrito,
                       BOOL_OR(e.tipo = 'PURCHASE')                       AS hubo_compra
                  FROM servida s
                  JOIN catalogo.evento_interaccion e
                    ON e.sujeto_id = s.sujeto_id
                   AND e.item_id = s.item_id
                   AND e.ocurrido_en >= s.servido_en
                   AND e.ocurrido_en < s.servido_en + make_interval(hours => :horas)
                 GROUP BY s.id
            )
            SELECT s.categoria_id, s.banda,
                   COUNT(*), COUNT(v.id),
                   COUNT(*) FILTER (WHERE a.hubo_clic),
                   COUNT(*) FILTER (WHERE a.hubo_profunda),
                   COUNT(*) FILTER (WHERE a.hubo_carrito),
                   COUNT(*) FILTER (WHERE a.hubo_compra),
                   COUNT(DISTINCT s.sujeto_id)
              FROM servida s
              LEFT JOIN vista v ON v.id = s.id
              LEFT JOIN accion a ON a.id = s.id
             GROUP BY 1, 2
             ORDER BY 1, 2
            """, nativeQuery = true)
    List<Object[]> embudoPorCategoriaYBanda(@Param("desde") Instant desde,
            @Param("hasta") Instant hasta, @Param("horas") int horas,
            @Param("modulo") String modulo);

    /**
     * El mismo embudo partido por módulo y razón, sin aplastar ninguna.
     *
     * <p>La razón se conserva tal cual la anotó el registro. El carrusel
     * colaborativo mezcla {@code CO_VIEWED} y {@code SIMILAR_SUBJECT} en la
     * misma pantalla, y la ficha mezcla {@code CONTENT_SIMILAR} con co-visita:
     * agruparlas aquí haría imposible saber cuál de los dos generadores acierta,
     * que es exactamente lo que la medición existe para responder. Si un informe
     * necesita juntarlas, que las junte arriba.
     *
     * @return filas {@code [categoriaId, modulo, razon, rankerVersion, conPerfil,
     *     servidas, vistas, clics, profundas, carritos, compras, sujetos]}
     */
    @Query(value = """
            WITH elegible AS (
                SELECT p.id, p.categoria_id
                  FROM catalogo.producto p
                 WHERE p.estado_moderacion = 'APROBADO' AND p.stock > 0
            ),
            servida AS (
                SELECT r.id, r.sujeto_id, r.item_id, r.modulo, r.razon,
                       r.ranker_version, r.con_perfil, r.servido_en, e.categoria_id
                  FROM catalogo.recomendacion_servida r
                  JOIN elegible e ON e.id = r.item_id
                 WHERE r.item_tipo = 'PRODUCTO'
                   AND r.servido_en >= :desde AND r.servido_en < :hasta
                   AND (CAST(:modulo AS text) IS NULL
                        OR r.modulo = CAST(:modulo AS text))
            ),
            vista AS (
                SELECT s.id
                  FROM servida s
                  JOIN catalogo.impresion i
                    ON i.sujeto_id = s.sujeto_id
                   AND i.item_id = s.item_id
                   AND i.modulo = s.modulo
                   AND i.mostrado_en >= s.servido_en
                   AND i.mostrado_en < s.servido_en + make_interval(hours => :horas)
                 GROUP BY s.id
            ),
            accion AS (
                SELECT s.id,
                       BOOL_OR(e.tipo IN ('ITEM_VIEW', 'ITEM_VIEW_DEEP')) AS hubo_clic,
                       BOOL_OR(e.tipo = 'ITEM_VIEW_DEEP')                 AS hubo_profunda,
                       BOOL_OR(e.tipo = 'ADD_TO_CART')                    AS hubo_carrito,
                       BOOL_OR(e.tipo = 'PURCHASE')                       AS hubo_compra
                  FROM servida s
                  JOIN catalogo.evento_interaccion e
                    ON e.sujeto_id = s.sujeto_id
                   AND e.item_id = s.item_id
                   AND e.ocurrido_en >= s.servido_en
                   AND e.ocurrido_en < s.servido_en + make_interval(hours => :horas)
                 GROUP BY s.id
            )
            SELECT s.categoria_id, s.modulo, s.razon, s.ranker_version, s.con_perfil,
                   COUNT(*), COUNT(v.id),
                   COUNT(*) FILTER (WHERE a.hubo_clic),
                   COUNT(*) FILTER (WHERE a.hubo_profunda),
                   COUNT(*) FILTER (WHERE a.hubo_carrito),
                   COUNT(*) FILTER (WHERE a.hubo_compra),
                   COUNT(DISTINCT s.sujeto_id)
              FROM servida s
              LEFT JOIN vista v ON v.id = s.id
              LEFT JOIN accion a ON a.id = s.id
             GROUP BY 1, 2, 3, 4, 5
             ORDER BY 1, 2, 3
            """, nativeQuery = true)
    List<Object[]> embudoPorCategoriaYRazon(@Param("desde") Instant desde,
            @Param("hasta") Instant hasta, @Param("horas") int horas,
            @Param("modulo") String modulo);
}
