package com.backend.catalogo.producto;

import java.time.Instant;
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

    /**
     * La vitrina, por páginas.
     *
     * <p>Antes esto devolvía una {@code List} con TODO el catálogo aprobado, y
     * era la respuesta más pesada del endpoint más visitado: la portada se
     * descargaba cada producto de la tienda para enseñar diez, y el panel de
     * administración lo mismo para pintar una tabla.
     */
    @Query(value = """
            SELECT p FROM Producto p
            LEFT JOIN FETCH p.categoria
            LEFT JOIN FETCH p.marca
            WHERE p.estadoModeracion = com.backend.catalogo.producto.EstadoModeracion.APROBADO
            """,
            countQuery = """
                    SELECT COUNT(p) FROM Producto p
                    WHERE p.estadoModeracion = com.backend.catalogo.producto.EstadoModeracion.APROBADO
                    """)
    Page<Producto> listarConRelaciones(Pageable pageable);

    @Query(value = """
            SELECT p FROM Producto p
            LEFT JOIN FETCH p.categoria
            LEFT JOIN FETCH p.marca
            WHERE p.estadoModeracion = com.backend.catalogo.producto.EstadoModeracion.APROBADO
              AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(p.description) LIKE LOWER(CONCAT('%', :texto, '%')))
            """,
            countQuery = """
                    SELECT COUNT(p) FROM Producto p
                    WHERE p.estadoModeracion = com.backend.catalogo.producto.EstadoModeracion.APROBADO
                      AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :texto, '%'))
                           OR LOWER(p.description) LIKE LOWER(CONCAT('%', :texto, '%')))
                    """)
    Page<Producto> buscarPorTexto(@Param("texto") String texto, Pageable pageable);

    /**
     * Lo que tiene descuento vigente AHORA, para el carrusel de ofertas.
     *
     * <p>El filtro va aquí y no en el navegador: la portada se traía el catálogo
     * entero para quedarse con los que estaban en oferta, que suelen ser un
     * puñado. La vigencia es la misma regla que aplica {@code precioEfectivo}:
     * hay precio de oferta y la fecha de hoy cae dentro, con los extremos
     * abiertos cuando no se han fijado.
     */
    @Query("""
            SELECT p FROM Producto p
            LEFT JOIN FETCH p.categoria
            LEFT JOIN FETCH p.marca
            WHERE p.estadoModeracion = com.backend.catalogo.producto.EstadoModeracion.APROBADO
              AND p.precioOferta IS NOT NULL
              AND (p.ofertaInicio IS NULL OR p.ofertaInicio <= :ahora)
              AND (p.ofertaFin IS NULL OR p.ofertaFin >= :ahora)
            ORDER BY p.id
            """)
    List<Producto> listarEnOferta(@Param("ahora") Instant ahora, Pageable pageable);

    /** Los N primeros de una categoría, para los bloques de la portada. */
    @Query("""
            SELECT p FROM Producto p
            LEFT JOIN FETCH p.categoria c
            LEFT JOIN FETCH p.marca
            WHERE c.id = :categoriaId
              AND p.estadoModeracion = com.backend.catalogo.producto.EstadoModeracion.APROBADO
            ORDER BY p.id
            """)
    List<Producto> listarDeCategoria(@Param("categoriaId") Long categoriaId, Pageable pageable);

    /* ── Panel de descuentos ── */

    /**
     * El listado del panel de descuentos, con sus cuatro filtros en el servidor.
     *
     * <p>Se filtraba entero en el navegador sobre el catálogo completo. Los
     * cuatro filtros van opcionales: un {@code null} desactiva el suyo, que es
     * el idioma de JPQL para esto y evita tener cuatro consultas casi iguales.
     *
     * <p>El estado del descuento es la misma regla que aplica la tienda:
     * <b>activo</b> si hay precio de oferta y hoy cae dentro de la vigencia,
     * <b>programado</b> si la vigencia aún no ha empezado, e <b>inactivo</b> si
     * no hay descuento o ya venció.
     */
    @Query(value = """
            SELECT p FROM Producto p
            LEFT JOIN FETCH p.categoria
            LEFT JOIN FETCH p.marca
            WHERE p.estadoModeracion = com.backend.catalogo.producto.EstadoModeracion.APROBADO
              AND (:categoriaId IS NULL OR p.categoria.id = :categoriaId)
              AND (:marcaId IS NULL OR p.marca.id = :marcaId)
              AND (:texto IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :texto, '%')))
              AND (:estado = 'todos'
                   OR (:estado = 'activo' AND p.precioOferta IS NOT NULL
                       AND (p.ofertaInicio IS NULL OR p.ofertaInicio <= :ahora)
                       AND (p.ofertaFin IS NULL OR p.ofertaFin >= :ahora))
                   OR (:estado = 'programado' AND p.precioOferta IS NOT NULL
                       AND p.ofertaInicio IS NOT NULL AND p.ofertaInicio > :ahora)
                   OR (:estado = 'inactivo' AND (p.precioOferta IS NULL
                       OR (p.ofertaFin IS NOT NULL AND p.ofertaFin < :ahora))))
            """,
            countQuery = """
                    SELECT COUNT(p) FROM Producto p
                    WHERE p.estadoModeracion = com.backend.catalogo.producto.EstadoModeracion.APROBADO
                      AND (:categoriaId IS NULL OR p.categoria.id = :categoriaId)
                      AND (:marcaId IS NULL OR p.marca.id = :marcaId)
                      AND (:texto IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :texto, '%')))
                      AND (:estado = 'todos'
                           OR (:estado = 'activo' AND p.precioOferta IS NOT NULL
                               AND (p.ofertaInicio IS NULL OR p.ofertaInicio <= :ahora)
                               AND (p.ofertaFin IS NULL OR p.ofertaFin >= :ahora))
                           OR (:estado = 'programado' AND p.precioOferta IS NOT NULL
                               AND p.ofertaInicio IS NOT NULL AND p.ofertaInicio > :ahora)
                           OR (:estado = 'inactivo' AND (p.precioOferta IS NULL
                               OR (p.ofertaFin IS NOT NULL AND p.ofertaFin < :ahora))))
                    """)
    Page<Producto> listarParaDescuentos(
            @Param("estado") String estado,
            @Param("categoriaId") Long categoriaId,
            @Param("marcaId") Long marcaId,
            @Param("texto") String texto,
            @Param("ahora") Instant ahora,
            Pageable pageable);

    /**
     * Cuántos hay en cada sección, sobre TODO el catálogo aprobado.
     *
     * <p>Deliberadamente sin los filtros de la pantalla: las pestañas cuentan
     * el catálogo entero, igual que cuando esto se calculaba en el navegador.
     * Si contaran lo filtrado, el número bailaría al escribir en el buscador y
     * dejaría de servir para lo que sirve, que es saber cuánto hay de cada cosa.
     *
     * <p>El `COALESCE` no es adorno: sin filas, `SUM` devuelve null y el
     * conteo llegaría vacío en vez de en cero.
     */
    @Query("""
            SELECT
              COALESCE(SUM(CASE WHEN p.precioOferta IS NOT NULL
                                 AND (p.ofertaInicio IS NULL OR p.ofertaInicio <= :ahora)
                                 AND (p.ofertaFin IS NULL OR p.ofertaFin >= :ahora)
                            THEN 1 ELSE 0 END), 0) AS activos,
              COALESCE(SUM(CASE WHEN p.precioOferta IS NOT NULL
                                 AND p.ofertaInicio IS NOT NULL AND p.ofertaInicio > :ahora
                            THEN 1 ELSE 0 END), 0) AS programados,
              COALESCE(SUM(CASE WHEN p.precioOferta IS NULL
                                 OR (p.ofertaFin IS NOT NULL AND p.ofertaFin < :ahora)
                            THEN 1 ELSE 0 END), 0) AS inactivos,
              COUNT(p) AS todos
            FROM Producto p
            WHERE p.estadoModeracion = com.backend.catalogo.producto.EstadoModeracion.APROBADO
            """)
    ConteoDescuentos contarPorEstadoDeDescuento(@Param("ahora") Instant ahora);

    /** Proyección de los cuatro conteos del panel de descuentos. */
    interface ConteoDescuentos {
        long getActivos();

        long getProgramados();

        long getInactivos();

        long getTodos();
    }

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
