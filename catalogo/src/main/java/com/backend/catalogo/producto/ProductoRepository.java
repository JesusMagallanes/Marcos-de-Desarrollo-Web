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
            """,
            countQuery = "SELECT COUNT(p) FROM Producto p WHERE p.categoria.slug = :slug")
    Page<Producto> listarPorCategoriaSlug(@Param("slug") String slug, Pageable pageable);

    @Query("""
            SELECT p FROM Producto p
            LEFT JOIN FETCH p.categoria
            LEFT JOIN FETCH p.marca
            WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :texto, '%'))
               OR LOWER(p.description) LIKE LOWER(CONCAT('%', :texto, '%'))
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
}
