package com.backend.catalogo.valoracion;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ValoracionRepository extends JpaRepository<Valoracion, Long> {

    List<Valoracion> findByProductoIdOrderByCreadoEnDesc(Long productoId);

    Optional<Valoracion> findByProductoIdAndUsuarioId(Long productoId, Long usuarioId);

    /**
     * Promedio y cantidad de valoraciones agrupados por producto. Los alias del
     * `SELECT` deben coincidir con los getters de la proyección.
     */
    @Query("""
            SELECT v.producto.id AS productoId,
                   AVG(v.calificacion) AS promedio,
                   COUNT(v) AS cantidad
            FROM Valoracion v
            WHERE v.producto.id IN :ids
            GROUP BY v.producto.id
            """)
    List<ResumenValoracion> resumenPorProductos(@Param("ids") Collection<Long> ids);

    interface ResumenValoracion {
        Long getProductoId();

        Double getPromedio();

        Long getCantidad();
    }
}
