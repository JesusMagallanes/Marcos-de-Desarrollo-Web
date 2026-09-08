package com.backend.catalogo.descubrimiento;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemDescartadoRepository extends JpaRepository<ItemDescartado, ItemDescartado.Id> {

    /** Todo lo que este sujeto no quiere volver a ver. Filtro duro. */
    @Query("""
            SELECT d.id.itemId FROM ItemDescartado d
             WHERE d.id.sujetoId = :sujeto AND d.id.itemTipo = :tipo
            """)
    List<Long> idsDescartados(@Param("sujeto") UUID sujeto, @Param("tipo") TipoItem tipo);
}
