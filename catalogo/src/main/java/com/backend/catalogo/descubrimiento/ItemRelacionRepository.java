package com.backend.catalogo.descubrimiento;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * El cálculo de «esto va con aquello», entero dentro de PostgreSQL.
 *
 * <p>Podría hacerse en Java trayéndose los eventos, y sería un error: son
 * cientos de miles de filas para producir unos pocos miles de relaciones. La
 * agregación es exactamente lo que una base de datos hace mejor que cualquier
 * bucle, y hacerla aquí evita que el tamaño del histórico se convierta en
 * memoria del proceso.
 */
public interface ItemRelacionRepository extends JpaRepository<ItemRelacion, ItemRelacion.Id> {

    /**
     * Reconstruye las relaciones de la ventana.
     *
     * <p>Cuatro decisiones viven en esta consulta y conviene leerlas juntas.
     *
     * <p><b>Un sujeto cuenta una vez.</b> El {@code GROUP BY sujeto, item}
     * inicial convierte cuatro visitas de la misma persona al mismo producto en
     * una sola fila. Sin eso, alguien que refresca una ficha veinte veces
     * pesaría como veinte personas distintas y podría fabricar él solo una
     * relación falsa.
     *
     * <p><b>Nadie aporta ilimitadamente.</b> El tope por sujeto acota el
     * autojoin, que es cuadrático en el número de ítems de CADA sujeto. Con un
     * tope de doscientos, el peor sujeto imaginable aporta cuarenta mil pares y
     * no cuatro millones. Se quedan los más recientes, que son los que
     * describen su interés actual.
     *
     * <p><b>Los eventos válidos son los que cuestan algo.</b> Ver, ver a fondo,
     * añadir al carrito, marcar favorito y comprar. Ni las impresiones —que solo
     * dicen que el sistema lo enseñó, no que a nadie le interesara— ni las
     * búsquedas ni los descartes.
     *
     * <p><b>El score divide por la popularidad.</b> Ver la explicación larga en
     * la migración V22: es lo único que impide que el superventas de la tienda
     * acabe siendo «parecido» a todo el catálogo.
     *
     * @return filas insertadas o actualizadas
     */
    @Modifying
    @Query(value = """
            WITH interaccion AS (
                SELECT e.sujeto_id, e.item_id, MAX(e.ocurrido_en) AS ultimo
                  FROM catalogo.evento_interaccion e
                 WHERE e.item_tipo = 'PRODUCTO'
                   AND e.item_id IS NOT NULL
                   AND e.ocurrido_en >= :desde
                   AND e.tipo IN ('ITEM_VIEW', 'ITEM_VIEW_DEEP', 'ADD_TO_CART',
                                  'FAVORITE', 'PURCHASE')
                 GROUP BY e.sujeto_id, e.item_id
            ),
            acotada AS (
                SELECT sujeto_id, item_id
                  FROM (SELECT i.sujeto_id, i.item_id,
                               ROW_NUMBER() OVER (PARTITION BY i.sujeto_id
                                                  ORDER BY i.ultimo DESC) AS n
                          FROM interaccion i) t
                 WHERE t.n <= :topeItemsPorSujeto
            ),
            popularidad AS (
                SELECT item_id, COUNT(*) AS sujetos FROM acotada GROUP BY item_id
            ),
            par AS (
                SELECT a.item_id AS item_a, b.item_id AS item_b, COUNT(*) AS soporte
                  FROM acotada a
                  JOIN acotada b ON b.sujeto_id = a.sujeto_id AND b.item_id <> a.item_id
                 GROUP BY a.item_id, b.item_id
                HAVING COUNT(*) >= :minSoporte
            )
            INSERT INTO catalogo.item_relacion
                   (item_tipo, item_a, item_b, score, soporte, ventana_dias, calculado_en)
            SELECT 'PRODUCTO', par.item_a, par.item_b,
                   CAST(par.soporte AS numeric)
                     / SQRT(CAST(pa.sujetos AS numeric) * CAST(pb.sujetos AS numeric)),
                   par.soporte, CAST(:ventanaDias AS smallint), CAST(:ahora AS timestamptz)
              FROM par
              JOIN popularidad pa ON pa.item_id = par.item_a
              JOIN popularidad pb ON pb.item_id = par.item_b
            ON CONFLICT (item_tipo, item_a, item_b) DO UPDATE SET
                score        = EXCLUDED.score,
                soporte      = EXCLUDED.soporte,
                ventana_dias = EXCLUDED.ventana_dias,
                calculado_en = EXCLUDED.calculado_en
            """, nativeQuery = true)
    int recalcular(@Param("desde") Instant desde,
            @Param("minSoporte") int minSoporte,
            @Param("topeItemsPorSujeto") int topeItemsPorSujeto,
            @Param("ventanaDias") int ventanaDias,
            @Param("ahora") Instant ahora);

    /**
     * Borra lo que esta pasada no volvió a escribir.
     *
     * <p>Es lo que hace que una relación pueda MORIR. Sin esto, dos productos
     * que se vieron juntos hace un año seguirían recomendándose para siempre, y
     * el sistema se volvería un archivo en vez de un reflejo de lo que pasa
     * ahora. El upsert de arriba refresca `calculado_en` de todo lo que sigue
     * vivo, así que lo que quedó atrás es exactamente lo que ya no se sostiene.
     */
    @Modifying
    @Query("DELETE FROM ItemRelacion r WHERE r.calculadoEn < :corte")
    int purgarObsoletas(@Param("corte") Instant corte);

    /** Los más relacionados con un ítem. Para pruebas y diagnóstico. */
    @Query("""
            SELECT r FROM ItemRelacion r
             WHERE r.id.itemTipo = :tipo AND r.id.itemA = :itemA
             ORDER BY r.score DESC
            """)
    List<ItemRelacion> desde(@Param("tipo") TipoItem tipo, @Param("itemA") Long itemA);
}
