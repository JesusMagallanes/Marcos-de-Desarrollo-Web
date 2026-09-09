package com.backend.catalogo.descubrimiento;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImpresionRepository extends JpaRepository<Impresion, Long> {

    /**
     * Los ítems que ya se le enseñaron N veces sin que los tocara.
     *
     * <p>Es el antídoto contra el carrusel que repite lo mismo cada día. Se
     * devuelve la lista entera de una vez y no ítem a ítem, porque se consulta
     * en cada armado del Home.
     */
    @Query(value = """
            SELECT i.item_id
              FROM catalogo.impresion i
             WHERE i.sujeto_id = :sujeto
               AND i.item_tipo = :itemTipo
               AND i.mostrado_en >= :desde
             GROUP BY i.item_id
            HAVING COUNT(*) FILTER (WHERE i.con_clic) = 0
               AND COUNT(*) >= :tope
            """, nativeQuery = true)
    List<Long> itemsConFatiga(@Param("sujeto") UUID sujeto, @Param("itemTipo") String itemTipo,
            @Param("desde") Instant desde, @Param("tope") int tope);

    /**
     * Marca como clicada la impresión más reciente de ese ítem.
     *
     * <p>Sin esto el CTR por módulo no se puede calcular: habría impresiones y
     * clics en tablas distintas sin forma de casarlos.
     */
    @Modifying
    @Query(value = """
            UPDATE catalogo.impresion SET con_clic = TRUE
             WHERE id = (SELECT i.id FROM catalogo.impresion i
                          WHERE i.sujeto_id = :sujeto AND i.item_id = :itemId
                            AND i.con_clic = FALSE
                          ORDER BY i.mostrado_en DESC LIMIT 1)
            """, nativeQuery = true)
    int marcarClic(@Param("sujeto") UUID sujeto, @Param("itemId") Long itemId);

    @Modifying
    @Query("DELETE FROM Impresion i WHERE i.mostradoEn < :limite")
    int purgarAnterioresA(@Param("limite") Instant limite);

    /**
     * Cuántas veces se ha enseñado cada uno de estos ítems, a quien sea.
     *
     * <p>Es la medida de popularidad EXPUESTA, que no es la misma que la de
     * popularidad real y es justamente la que hay que castigar: un producto se
     * ve mucho porque el sistema lo enseña mucho, y si eso lo hace subir en el
     * ranking, el sistema se está retroalimentando a sí mismo. Ver
     * {@code PesosDescubrimiento.factorPopularidad}.
     *
     * <p>Sobre la lista de candidatos ya recortada, no sobre el catálogo: son
     * unas decenas de identificadores y un recorrido de índice.
     *
     * @return filas {@code [itemId, veces]}
     */
    @Query(value = """
            SELECT i.item_id, COUNT(*)
              FROM catalogo.impresion i
             WHERE i.item_tipo = 'PRODUCTO'
               AND i.item_id IN (:items)
               AND i.mostrado_en >= :desde
             GROUP BY i.item_id
            """, nativeQuery = true)
    List<Object[]> contarPorItem(@Param("items") List<Long> items, @Param("desde") Instant desde);
}
