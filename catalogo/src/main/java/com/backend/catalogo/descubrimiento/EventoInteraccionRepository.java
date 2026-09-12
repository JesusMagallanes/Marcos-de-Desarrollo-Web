package com.backend.catalogo.descubrimiento;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventoInteraccionRepository extends JpaRepository<EventoInteraccion, Long> {

    /**
     * Cuántas veces ya hizo esto mismo en esta sesión.
     *
     * <p>Alimenta la saturación: refrescar una ficha veinte veces no son veinte
     * intereses.
     */
    @Query("""
            SELECT COUNT(e) FROM EventoInteraccion e
             WHERE e.sujetoId = :sujeto AND e.sesionId = :sesion
               AND e.tipo = :tipo AND e.itemId = :itemId
            """)
    long contarRepeticiones(@Param("sujeto") UUID sujeto, @Param("sesion") UUID sesion,
            @Param("tipo") TipoEvento tipo, @Param("itemId") Long itemId);

    /** Lo último que miró, para «sigue donde lo dejaste» y para no repetirlo. */
    @Query("""
            SELECT e.itemId FROM EventoInteraccion e
             WHERE e.sujetoId = :sujeto AND e.itemTipo = :tipo AND e.itemId IS NOT NULL
               AND e.tipo IN (com.backend.catalogo.descubrimiento.TipoEvento.ITEM_VIEW,
                              com.backend.catalogo.descubrimiento.TipoEvento.ITEM_VIEW_DEEP)
             GROUP BY e.itemId
             ORDER BY MAX(e.ocurridoEn) DESC
            """)
    List<Long> itemsVistosRecientemente(@Param("sujeto") UUID sujeto,
            @Param("tipo") TipoItem tipo, org.springframework.data.domain.Pageable pagina);

    /** Retención: los eventos crudos no se guardan para siempre. */
    /**
     * La sesión que sigue viva, si la hay.
     *
     * <p>El identificador de sesión lo genera el navegador y solo viaja en la
     * INGESTA: las peticiones de lectura —el Home, la ficha— no lo llevan. Así
     * que la sesión activa hay que deducirla, y la deducción natural es el
     * {@code sesion_id} del último evento del sujeto, siempre que sea lo
     * bastante reciente para que la sesión no se dé por terminada.
     *
     * <p>Deducirla en vez de recibirla tiene además una ventaja: no hace falta
     * cambiar el contrato ni el cliente, y no hay forma de que alguien mande
     * una sesión ajena.
     */
    @Query(value = """
            SELECT e.sesion_id
              FROM catalogo.evento_interaccion e
             WHERE e.sujeto_id = :sujeto
               AND e.sesion_id IS NOT NULL
               AND e.ocurrido_en >= :viva
             ORDER BY e.ocurrido_en DESC
             LIMIT 1
            """, nativeQuery = true)
    UUID sesionViva(@Param("sujeto") UUID sujeto, @Param("viva") Instant viva);

    /**
     * Las tres señales temporales de un sujeto, en UNA sola consulta.
     *
     * <p>Histórico, reciente e intención de sesión salen del mismo recorrido de
     * {@code evento_interaccion}, separadas por dos {@code FILTER}. Pedirlas por
     * separado serían tres consultas para leer las mismas filas.
     *
     * <h4>La saturación, dentro del SQL</h4>
     *
     * <p>Es el punto delicado. Sin ella, cien recargas de la misma ficha serían
     * cien intereses y una pestaña olvidada decidiría el Home entero. Se aplica
     * el mismo factor que ya usa el perfil —{@code 1/(1+ln n)}— pero por
     * (categoría, ítem, tipo): el segundo evento igual vale 0,59, el décimo
     * 0,30. Un interés repetido sigue contando más que uno aislado, pero deja de
     * crecer casi enseguida.
     *
     * <p>Los pesos por tipo de evento son los MISMOS que usa el perfil. No se
     * inventa ninguno: si mirar a fondo vale 2,5 al construir el perfil, vale
     * 2,5 aquí. `NOT_INTERESTED` y los descartes no aparecen — nunca pueden
     * convertirse en interés, ni de sesión ni de nada.
     *
     * @return filas {@code [categoria_id, peso_reciente, peso_sesion, interacciones_sesion]}
     */
    @Query(value = """
            WITH interaccion AS (
                SELECT COALESCE(e.categoria_id, p.categoria_id) AS categoria_id,
                       e.item_id,
                       e.tipo,
                       e.sesion_id,
                       MAX(e.ocurrido_en) AS ultimo,
                       COUNT(*) AS veces
                  FROM catalogo.evento_interaccion e
                  LEFT JOIN catalogo.producto p ON p.id = e.item_id
                 WHERE e.sujeto_id = :sujeto
                   AND e.ocurrido_en >= :desde
                   AND e.ocurrido_en <= :ahora
                   AND e.tipo IN ('ITEM_VIEW', 'ITEM_VIEW_DEEP', 'CATEGORY_VIEW',
                                  'SEARCH_CLICK', 'ATTRIBUTE_FILTER', 'ADD_TO_CART',
                                  'FAVORITE', 'PURCHASE')
                   AND COALESCE(e.categoria_id, p.categoria_id) IS NOT NULL
                 GROUP BY 1, 2, 3, 4
            ),
            puntuada AS (
                SELECT i.categoria_id,
                       i.sesion_id,
                       i.ultimo,
                       CASE i.tipo
                         WHEN 'PURCHASE'         THEN 20.0
                         WHEN 'ADD_TO_CART'      THEN 12.0
                         WHEN 'FAVORITE'         THEN 8.0
                         WHEN 'ATTRIBUTE_FILTER' THEN 3.0
                         WHEN 'SEARCH_CLICK'     THEN 3.0
                         WHEN 'ITEM_VIEW_DEEP'   THEN 2.5
                         WHEN 'ITEM_VIEW'        THEN 1.0
                         WHEN 'CATEGORY_VIEW'    THEN 0.5
                         ELSE 0 END
                       / (1 + LN(GREATEST(i.veces, 1))) AS peso
                  FROM interaccion i
            )
            SELECT q.categoria_id,
                   CAST(COALESCE(SUM(q.peso) FILTER (
                       WHERE q.ultimo >= :desdeReciente), 0) AS double precision),
                   CAST(COALESCE(SUM(q.peso) FILTER (
                       WHERE q.sesion_id = :sesion), 0) AS double precision),
                   /*
                    * Interacciones DISTINTAS de la sesion en esta categoria.
                    * Distintas ya de origen: `puntuada` viene agrupada por
                    * (item, tipo), asi que cien recargas del mismo producto son
                    * una. Es lo que decide si hay intencion suficiente, y contar
                    * categorias en su lugar —como hacia la primera version— dejaba
                    * fuera el caso mas comun: dos fichas de la misma categoria.
                    */
                   COUNT(*) FILTER (WHERE q.sesion_id = :sesion)
              FROM puntuada q
             GROUP BY q.categoria_id
             ORDER BY 3 DESC, 2 DESC
            """, nativeQuery = true)
    List<Object[]> senalesTemporales(@Param("sujeto") UUID sujeto,
            @Param("desde") Instant desde,
            @Param("desdeReciente") Instant desdeReciente,
            @Param("ahora") Instant ahora,
            @Param("sesion") UUID sesion);

    /**
     * Retención, por fin ejecutada.
     *
     * <p>Este método existía desde la fase 1 y no lo llamaba nadie: la tabla
     * crecía sin techo, que es exactamente el defecto que la fase 3 encontró en
     * las impresiones. Se conserva para las pruebas y para un borrado de golpe
     * si alguna vez hiciera falta; el mantenimiento usa {@link #purgarLote}.
     */
    @Modifying
    @Query("DELETE FROM EventoInteraccion e WHERE e.ocurridoEn < :limite")
    int purgarAnterioresA(@Param("limite") Instant limite);

    /**
     * Un lote de la purga, y no la purga entera.
     *
     * <h4>Por qué por lotes</h4>
     *
     * <p>La primera pasada tras desplegar esto se encuentra con todo el atraso
     * acumulado desde la fase 1. Un {@code DELETE} de golpe sobre eso es una
     * transacción de minutos que retiene bloqueos, hincha el WAL y, si se cae a
     * medias, deshace todo el trabajo. Un lote de unos miles de filas se
     * confirma en milisegundos y el siguiente continúa donde lo dejó.
     *
     * <h4>El índice que usa</h4>
     *
     * <p>{@code idx_evento_fecha (ocurrido_en)}, que V21 creó exactamente para
     * esto: «la purga por retención barre por fecha». El {@code ORDER BY} sobre
     * la misma columna hace que el subselect sea un recorrido corto del índice
     * desde el extremo antiguo. No hace falta ningún índice nuevo.
     *
     * @return cuántas filas se borraron; menos de {@code lote} significa que ya
     *     no queda nada vencido
     */
    @Modifying
    @Query(value = """
            DELETE FROM catalogo.evento_interaccion
             WHERE id IN (SELECT e.id
                            FROM catalogo.evento_interaccion e
                           WHERE e.ocurrido_en < :limite
                           ORDER BY e.ocurrido_en
                           LIMIT :lote)
            """, nativeQuery = true)
    int purgarLote(@Param("limite") Instant limite, @Param("lote") int lote);
}
