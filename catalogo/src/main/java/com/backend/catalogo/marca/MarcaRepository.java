package com.backend.catalogo.marca;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarcaRepository extends JpaRepository<Marca, Long> {

    boolean existsByName(String name);

    /**
     * Las marcas que venden en una categoría.
     *
     * <p>Era un método derivado del nombre ({@code findByCategoriaId…}) cuando
     * la categoría era una columna de la marca. Ahora la relación es M:N y hay
     * que atravesar la tabla intermedia, así que la consulta se escribe.
     */
    @Query("""
            SELECT DISTINCT m FROM Marca m
            JOIN m.categorias c
            LEFT JOIN FETCH m.categorias
            WHERE c.id = :categoriaId
            ORDER BY m.name ASC
            """)
    List<Marca> listarPorCategoria(@Param("categoriaId") Long categoriaId);

    /**
     * Todas, con sus categorías ya cargadas.
     *
     * <p>El {@code JOIN FETCH} no es adorno: la respuesta incluye la lista de
     * categorías de cada marca, y sin él el panel dispararía una consulta por
     * marca para pintar una tabla.
     */
    @Query("SELECT DISTINCT m FROM Marca m LEFT JOIN FETCH m.categorias ORDER BY m.name ASC")
    List<Marca> listarConCategorias();
}
