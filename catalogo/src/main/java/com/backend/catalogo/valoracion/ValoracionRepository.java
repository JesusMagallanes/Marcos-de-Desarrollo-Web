package com.backend.catalogo.valoracion;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ValoracionRepository extends JpaRepository<Valoracion, Long> {

    /** Solo las aprobadas: la tienda no muestra reseñas pendientes ni rechazadas. */
    List<Valoracion> findByProductoIdAndEstadoOrderByCreadoEnDesc(Long productoId, EstadoValoracion estado);

    Optional<Valoracion> findByProductoIdAndUsuarioId(Long productoId, Long usuarioId);

    /**
     * Las 6 mejor valoradas (más estrellas, desempate por las más recientes)
     * para la portada. Solo aprobadas: el top de la tienda no puede salir de
     * reseñas pendientes o rechazadas.
     */
    List<Valoracion> findTop6ByEstadoOrderByCalificacionDescCreadoEnDesc(EstadoValoracion estado);

    /** Panel de moderación: todas, de todos los productos. */
    List<Valoracion> findAllByOrderByCreadoEnDesc();

    /** Panel de moderación filtrado por estado. */
    List<Valoracion> findByEstadoOrderByCreadoEnDesc(EstadoValoracion estado);

    /**
     * Promedio y cantidad de valoraciones agrupados por producto. Los alias del
     * `SELECT` deben coincidir con los getters de la proyección. Solo cuentan
     * las aprobadas: un montón de reseñas pendientes no debe inflar la nota.
     */
    @Query("""
            SELECT v.producto.id AS productoId,
                   AVG(v.calificacion) AS promedio,
                   COUNT(v) AS cantidad
            FROM Valoracion v
            WHERE v.producto.id IN :ids
              AND v.estado = com.backend.catalogo.valoracion.EstadoValoracion.APROBADA
            GROUP BY v.producto.id
            """)
    List<ResumenValoracion> resumenPorProductos(@Param("ids") Collection<Long> ids);

    interface ResumenValoracion {
        Long getProductoId();

        Double getPromedio();

        Long getCantidad();
    }
}
