package com.backend.catalogo.producto;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    /**
     * `JOIN FETCH` en todas las lecturas de listado: sin esto cada producto
     * dispara dos consultas extra (categoría y marca), que era el N+1 que
     * tenía la portada del monolito.
     */
    @Query("""
            SELECT p FROM Producto p
            LEFT JOIN FETCH p.categoria
            LEFT JOIN FETCH p.marca
            WHERE p.estadoModeracion = com.backend.catalogo.producto.EstadoModeracion.APROBADO
            ORDER BY p.id
            """)
    List<Producto> listarConRelaciones();

    @Query("""
            SELECT p FROM Producto p
            LEFT JOIN FETCH p.categoria
            LEFT JOIN FETCH p.marca
            WHERE p.id = :id
            """)
    Optional<Producto> buscarConRelaciones(@Param("id") Long id);

    @Query(value = """
            SELECT p FROM Producto p
            LEFT JOIN FETCH p.categoria c
            LEFT JOIN FETCH p.marca
            WHERE c.slug = :slug
              AND p.estadoModeracion = com.backend.catalogo.producto.EstadoModeracion.APROBADO
            """,
            countQuery = """
                    SELECT COUNT(p) FROM Producto p
                    WHERE p.categoria.slug = :slug
                      AND p.estadoModeracion = com.backend.catalogo.producto.EstadoModeracion.APROBADO
                    """)
    Page<Producto> listarPorCategoriaSlug(@Param("slug") String slug, Pageable pageable);

    @Query("""
            SELECT p FROM Producto p
            LEFT JOIN FETCH p.categoria
            LEFT JOIN FETCH p.marca
            WHERE p.estadoModeracion = com.backend.catalogo.producto.EstadoModeracion.APROBADO
              AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(p.description) LIKE LOWER(CONCAT('%', :texto, '%')))
            ORDER BY p.id
            """)
    List<Producto> buscarPorTexto(@Param("texto") String texto);

    List<Producto> findByIdIn(List<Long> ids);

    /** Bloqueo pesimista para el descuento de stock desde el servicio de compras. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Producto p WHERE p.id = :id")
    Optional<Producto> buscarParaActualizarStock(@Param("id") Long id);

    boolean existsByCategoriaId(Long categoriaId);

    boolean existsByMarcaId(Long marcaId);

    /* ── Productos de colaborador (SZ-B08) ── */

    /**
     * Lo que ve el colaborador en su panel: todo lo suyo, esté aprobado o no.
     *
     * <p>A diferencia de las de arriba, esta NO filtra por estado: el dueño tiene
     * que ver sus rechazados para saber qué corregir.
     */
    @Query("""
            SELECT p FROM Producto p
            LEFT JOIN FETCH p.categoria
            LEFT JOIN FETCH p.marca
            WHERE p.propietarioId = :propietarioId
            ORDER BY p.id DESC
            """)
    List<Producto> listarDelPropietario(@Param("propietarioId") Long propietarioId);

    /** La cola de moderación. Lo más antiguo primero: nadie debe quedarse atrás. */
    @Query("""
            SELECT p FROM Producto p
            LEFT JOIN FETCH p.categoria
            LEFT JOIN FETCH p.marca
            WHERE p.estadoModeracion = :estado
              AND p.propietarioId IS NOT NULL
            ORDER BY p.id ASC
            """)
    List<Producto> listarPorEstadoModeracion(@Param("estado") EstadoModeracion estado);

    /** Cuántos lleva publicados, para el tope por colaborador. */
    long countByPropietarioId(Long propietarioId);
}
