package com.backend.catalogo.sincronizacion;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OperacionAplicadaRepository extends JpaRepository<OperacionAplicada, String> {

    /**
     * Purga de registros viejos.
     *
     * <p>El registro solo sirve para reconocer reenvíos, y los reintentos
     * dejan de llegar horas después de la operación original: con una semana
     * de margen sobra. Sin purga la tabla crece para siempre con una fila por
     * reseña escrita offline.
     */
    @Modifying
    @Query("DELETE FROM OperacionAplicada o WHERE o.creadoEn < :limite")
    int eliminarAnterioresA(@Param("limite") Instant limite);
}
