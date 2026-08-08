package com.backend.catalogo.inventario;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservaStockRepository extends JpaRepository<ReservaStock, Long> {

    List<ReservaStock> findByReferencia(String referencia);

    boolean existsByReferencia(String referencia);

    @Query("""
            SELECT r FROM ReservaStock r
            WHERE r.estado = com.backend.catalogo.inventario.ReservaStock$Estado.ACTIVA
              AND r.expiraEn < :limite
            """)
    List<ReservaStock> buscarCaducadas(@Param("limite") LocalDateTime limite);
}
