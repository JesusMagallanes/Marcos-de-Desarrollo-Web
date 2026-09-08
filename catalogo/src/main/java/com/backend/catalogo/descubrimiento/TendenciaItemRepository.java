package com.backend.catalogo.descubrimiento;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TendenciaItemRepository
        extends JpaRepository<TendenciaItem, TendenciaItem.Id> {

    /**
     * Recalcula las tendencias de un nivel geográfico.
     *
     * <p>El score NO es «lo más visto»: es la velocidad reciente ponderada por
     * su crecimiento frente al periodo anterior.
     *
     * <pre>
     *   score = recientes × (1 + ln(1 + recientes / max(previos, 1)))
     * </pre>
     *
     * <p>Con el ejemplo de siempre: A con 20 vistas esta semana y 20 la anterior
     * puntúa 20 × (1+ln 2) ≈ 34; B con 80 esta semana y 10 la anterior puntúa
     * 80 × (1+ln 9) ≈ 256. B es tendencia aunque A acumule más historia.
     *
     * <p>El {@code HAVING} sobre sujetos distintos es el piso de privacidad: una
     * fila sostenida por menos de N personas no se publica, porque en un
     * distrito pequeño delataría a quien la generó.
     */
    @Modifying
    @Query(value = """
            INSERT INTO catalogo.tendencia_item
                   (nivel, zona, item_tipo, item_id, categoria_id, score, sujetos, calculado_en)
            SELECT CAST(:nivel AS varchar), z.zona, z.item_tipo, z.item_id,
                   z.categoria_id, z.score, z.sujetos, now()
              FROM (
                    SELECT LEFT(COALESCE(e.ubigeo, ''), :digitos) AS zona,
                           e.item_tipo,
                           e.item_id,
                           MAX(e.categoria_id) AS categoria_id,
                           COUNT(*) FILTER (WHERE e.ocurrido_en >= :corte)
                             * (1 + LN(1 + CAST(COUNT(*) FILTER (WHERE e.ocurrido_en >= :corte)
                                                AS numeric)
                                         / GREATEST(COUNT(*) FILTER (WHERE e.ocurrido_en < :corte),
                                                    1))) AS score,
                           COUNT(DISTINCT e.sujeto_id) AS sujetos
                      FROM catalogo.evento_interaccion e
                     WHERE e.item_id IS NOT NULL
                       AND e.ocurrido_en >= :desde
                       AND (:digitos = 0
                            OR (e.ubigeo IS NOT NULL AND LENGTH(e.ubigeo) >= :digitos))
                     -- Se agrupa por ORDINAL y no repitiendo la expresion: cada
                     -- `:digitos` se expande a un marcador distinto y Postgres
                     -- no reconoceria que el SELECT y el GROUP BY son lo mismo.
                     GROUP BY 1, e.item_tipo, e.item_id
                    HAVING COUNT(DISTINCT e.sujeto_id) >= :minimoSujetos
                       AND COUNT(*) FILTER (WHERE e.ocurrido_en >= :corte) > 0
                   ) z
            ON CONFLICT (nivel, zona, item_tipo, item_id) DO UPDATE SET
                score = EXCLUDED.score,
                sujetos = EXCLUDED.sujetos,
                categoria_id = EXCLUDED.categoria_id,
                calculado_en = now()
            """, nativeQuery = true)
    int recalcular(@Param("nivel") String nivel, @Param("digitos") int digitos,
            @Param("desde") Instant desde, @Param("corte") Instant corte,
            @Param("minimoSujetos") int minimoSujetos);

    /** Lo que ya no se recalculó en la última pasada deja de ser tendencia. */
    @Modifying
    @Query("DELETE FROM TendenciaItem t WHERE t.id.nivel = :nivel AND t.calculadoEn < :limite")
    int purgarObsoletas(@Param("nivel") NivelGeografico nivel, @Param("limite") Instant limite);

    @Query("""
            SELECT t FROM TendenciaItem t
             WHERE t.id.nivel = :nivel AND t.id.zona = :zona AND t.id.itemTipo = :tipo
             ORDER BY t.score DESC
            """)
    List<TendenciaItem> masFuertes(@Param("nivel") NivelGeografico nivel,
            @Param("zona") String zona, @Param("tipo") TipoItem tipo, Pageable pagina);
}
