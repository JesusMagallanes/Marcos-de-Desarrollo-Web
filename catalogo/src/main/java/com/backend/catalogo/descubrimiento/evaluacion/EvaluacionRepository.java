package com.backend.catalogo.descubrimiento.evaluacion;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import com.backend.catalogo.descubrimiento.EventoInteraccion;

/**
 * Las señales del recomendador, reconstruidas a una fecha del pasado.
 *
 * <h4>Por qué no se reutilizan las consultas normales</h4>
 *
 * <p>Sería lo cómodo y sería inválido. `perfil_faceta`, `item_relacion`,
 * `sujeto_similitud` y `tendencia_item` son tablas de ESTADO ACTUAL: se
 * actualizan en sitio y no guardan historia, así que reflejan todo lo que ha
 * pasado hasta hoy. Usarlas para evaluar un corte del mes pasado significaría
 * construir la recomendación con información que en ese momento no existía, y
 * luego felicitarse por acertar. Es la forma más común de fabricar una
 * evaluación que miente, y precisamente por cómoda.
 *
 * <p>Aquí todo sale de {@code evento_interaccion}, que es la única tabla con
 * fecha por fila y por tanto la única que se puede rebobinar. Cada consulta
 * lleva {@code < :corte} y ninguna toca una tabla derivada. Es más lento y es lo
 * único honesto.
 */
public interface EvaluacionRepository extends JpaRepository<EventoInteraccion, Long> {

    /**
     * PERSONAL · productos de las categorías que le interesaban en el corte.
     *
     * <p>Reconstruye el perfil desde los eventos en vez de leerlo: afinidad por
     * categoría = suma de los pesos de sus eventos, y de ahí a los productos de
     * esas categorías ponderados por su valoración. Es la fase 1 en pequeño, sin
     * jerarquía ni atributos, que es lo que se puede sostener sin arrastrar la
     * historia entera del perfil.
     *
     * <p>Se excluye lo que ya tocó antes del corte: recomendarle lo que acaba de
     * ver no es un acierto aunque vuelva a mirarlo.
     *
     * @return filas {@code [sujeto, item, score]}
     */
    @Query(value = """
            WITH evento AS (
                SELECT e.sujeto_id, e.item_id, e.categoria_id, e.tipo
                  FROM catalogo.evento_interaccion e
                 WHERE e.ocurrido_en >= :desde AND e.ocurrido_en < :corte
            ),
            afinidad AS (
                SELECT v.sujeto_id, COALESCE(v.categoria_id, p.categoria_id) AS categoria_id,
                       SUM(CASE v.tipo
                             WHEN 'PURCHASE'       THEN 20
                             WHEN 'ADD_TO_CART'    THEN 12
                             WHEN 'FAVORITE'       THEN 8
                             WHEN 'ITEM_VIEW_DEEP' THEN 2.5
                             WHEN 'ITEM_VIEW'      THEN 1
                             WHEN 'CATEGORY_VIEW'  THEN 0.5
                             ELSE 0 END) AS peso
                  FROM evento v
                  LEFT JOIN catalogo.producto p ON p.id = v.item_id
                 WHERE COALESCE(v.categoria_id, p.categoria_id) IS NOT NULL
                 GROUP BY 1, 2
                HAVING SUM(CASE v.tipo
                             WHEN 'PURCHASE'       THEN 20
                             WHEN 'ADD_TO_CART'    THEN 12
                             WHEN 'FAVORITE'       THEN 8
                             WHEN 'ITEM_VIEW_DEEP' THEN 2.5
                             WHEN 'ITEM_VIEW'      THEN 1
                             WHEN 'CATEGORY_VIEW'  THEN 0.5
                             ELSE 0 END) > 0
            ),
            tocado AS (
                SELECT DISTINCT sujeto_id, item_id FROM evento WHERE item_id IS NOT NULL
            )
            SELECT a.sujeto_id, p.id,
                   CAST(a.peso * (1 + COALESCE(v.nota, 3.5) / 5.0) AS double precision)
              FROM afinidad a
              JOIN catalogo.producto p ON p.categoria_id = a.categoria_id
              LEFT JOIN (SELECT producto_id, AVG(calificacion) AS nota
                           FROM catalogo.valoracion GROUP BY producto_id) v ON v.producto_id = p.id
             WHERE p.estado_moderacion = 'APROBADO'
               AND p.stock > 0
               AND NOT EXISTS (SELECT 1 FROM tocado t
                                WHERE t.sujeto_id = a.sujeto_id AND t.item_id = p.id)
            """, nativeQuery = true)
    List<Object[]> candidatosPersonales(@Param("desde") Instant desde,
            @Param("corte") Instant corte);

    /**
     * COLABORATIVO · co-visitas construidas con lo anterior al corte.
     *
     * <p>Mismo coseno que en producción, misma deduplicación por sujeto y mismo
     * soporte mínimo. Lo que cambia es de dónde salen los datos: aquí se
     * recalcula sobre la marcha en vez de leer `item_relacion`, que ya contiene
     * el futuro.
     *
     * @return filas {@code [sujeto, item, score]} ya proyectadas al sujeto
     */
    @Query(value = """
            WITH interaccion AS (
                SELECT DISTINCT e.sujeto_id, e.item_id
                  FROM catalogo.evento_interaccion e
                 WHERE e.item_tipo = 'PRODUCTO' AND e.item_id IS NOT NULL
                   AND e.ocurrido_en >= :desde AND e.ocurrido_en < :corte
                   AND e.tipo IN ('ITEM_VIEW', 'ITEM_VIEW_DEEP', 'ADD_TO_CART',
                                  'FAVORITE', 'PURCHASE')
            ),
            popularidad AS (
                SELECT item_id, COUNT(*) AS sujetos FROM interaccion GROUP BY item_id
            ),
            relacion AS (
                SELECT a.item_id AS item_a, b.item_id AS item_b, COUNT(*) AS soporte
                  FROM interaccion a
                  JOIN interaccion b ON b.sujeto_id = a.sujeto_id AND b.item_id <> a.item_id
                 GROUP BY 1, 2
                HAVING COUNT(*) >= :minSoporte
            ),
            puntuada AS (
                SELECT r.item_a, r.item_b,
                       CAST(r.soporte AS numeric)
                         / SQRT(CAST(pa.sujetos AS numeric) * CAST(pb.sujetos AS numeric)) AS score
                  FROM relacion r
                  JOIN popularidad pa ON pa.item_id = r.item_a
                  JOIN popularidad pb ON pb.item_id = r.item_b
            )
            SELECT i.sujeto_id, q.item_b, CAST(SUM(q.score) AS double precision)
              FROM interaccion i
              JOIN puntuada q ON q.item_a = i.item_id
              JOIN catalogo.producto p ON p.id = q.item_b
             WHERE p.estado_moderacion = 'APROBADO' AND p.stock > 0
               AND NOT EXISTS (SELECT 1 FROM interaccion yo
                                WHERE yo.sujeto_id = i.sujeto_id AND yo.item_id = q.item_b)
             GROUP BY 1, 2
            """, nativeQuery = true)
    List<Object[]> candidatosColaborativos(@Param("desde") Instant desde,
            @Param("corte") Instant corte,
            @Param("minSoporte") int minSoporte);

    /**
     * TENDENCIA · lo que se movía antes del corte.
     *
     * @return filas {@code [item, score]}
     */
    @Query(value = """
            SELECT e.item_id, CAST(COUNT(DISTINCT e.sujeto_id) AS double precision)
              FROM catalogo.evento_interaccion e
              JOIN catalogo.producto p ON p.id = e.item_id
             WHERE e.item_tipo = 'PRODUCTO'
               AND e.ocurrido_en >= :desde AND e.ocurrido_en < :corte
               AND e.tipo IN ('ITEM_VIEW', 'ITEM_VIEW_DEEP', 'ADD_TO_CART',
                              'FAVORITE', 'PURCHASE')
               AND p.estado_moderacion = 'APROBADO' AND p.stock > 0
             GROUP BY e.item_id
            """, nativeQuery = true)
    List<Object[]> popularidadEnCorte(@Param("desde") Instant desde,
            @Param("corte") Instant corte);

    /**
     * LA VERDAD · lo que cada sujeto hizo DESPUÉS del corte.
     *
     * <p>Solo acciones que revelan interés de verdad. Una impresión no cuenta:
     * dice que el sistema lo enseñó, no que a nadie le importara, y contarla
     * como acierto sería evaluar al recomendador contra sí mismo.
     *
     * @return filas {@code [sujeto, item]}
     */
    @Query(value = """
            SELECT DISTINCT e.sujeto_id, e.item_id
              FROM catalogo.evento_interaccion e
             WHERE e.item_tipo = 'PRODUCTO' AND e.item_id IS NOT NULL
               AND e.ocurrido_en >= :corte AND e.ocurrido_en < :fin
               AND e.tipo IN ('ITEM_VIEW', 'ITEM_VIEW_DEEP', 'ADD_TO_CART',
                              'FAVORITE', 'PURCHASE')
            """, nativeQuery = true)
    List<Object[]> holdout(@Param("corte") Instant corte, @Param("fin") Instant fin);

    /**
     * Cuánta historia tenía cada sujeto en el corte.
     *
     * <p>Separa el arranque en frío del resto. Mezclarlos esconde el problema
     * más importante que puede tener un recomendador: que funcione muy bien para
     * quien ya lo usa y no tenga nada que ofrecer a quien llega.
     *
     * @return filas {@code [sujeto, eventos]}
     */
    @Query(value = """
            SELECT e.sujeto_id, COUNT(*)
              FROM catalogo.evento_interaccion e
             WHERE e.ocurrido_en >= :desde AND e.ocurrido_en < :corte
             GROUP BY e.sujeto_id
            """, nativeQuery = true)
    List<Object[]> historialEnCorte(@Param("desde") Instant desde,
            @Param("corte") Instant corte);

    /**
     * Lo que cada sujeto ya había tocado en el corte.
     *
     * <p>Es el denominador de la repetición. Los generadores excluyen esto por
     * construcción, así que la métrica debería salir 0 siempre — y por eso hay
     * que medirla: un 0 calculado demuestra que la exclusión funciona, y si
     * alguien la rompe, sube. Un 0 escrito a mano no demostraría nada.
     *
     * @return filas {@code [sujeto, item]}
     */
    @Query(value = """
            SELECT DISTINCT e.sujeto_id, e.item_id
              FROM catalogo.evento_interaccion e
             WHERE e.item_tipo = 'PRODUCTO' AND e.item_id IS NOT NULL
               AND e.ocurrido_en >= :desde AND e.ocurrido_en < :corte
            """, nativeQuery = true)
    List<Object[]> yaTocadoEnCorte(@Param("desde") Instant desde, @Param("corte") Instant corte);

    /** Categoría y marca de cada producto, para medir diversidad. */
    @Query(value = """
            SELECT p.id, p.categoria_id, p.marca_id
              FROM catalogo.producto p
             WHERE p.estado_moderacion = 'APROBADO' AND p.stock > 0
            """, nativeQuery = true)
    List<Object[]> fichaDeCatalogo();

    /** Cuántos productos podrían recomendarse. El denominador de la cobertura. */
    @Query(value = """
            SELECT COUNT(*) FROM catalogo.producto p
             WHERE p.estado_moderacion = 'APROBADO' AND p.stock > 0
            """, nativeQuery = true)
    long catalogoElegible();
}
