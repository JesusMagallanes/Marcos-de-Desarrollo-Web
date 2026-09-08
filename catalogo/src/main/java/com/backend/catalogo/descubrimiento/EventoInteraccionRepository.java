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
    @Modifying
    @Query("DELETE FROM EventoInteraccion e WHERE e.ocurridoEn < :limite")
    int purgarAnterioresA(@Param("limite") Instant limite);
}
