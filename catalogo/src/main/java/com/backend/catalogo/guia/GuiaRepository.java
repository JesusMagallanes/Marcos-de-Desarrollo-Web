package com.backend.catalogo.guia;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuiaRepository extends JpaRepository<Guia, Long> {

    /** Listado público: solo lo publicado, en el orden que fijó el administrador. */
    List<Guia> findByPublicadaTrueOrderByPosicionAscTituloAsc();

    /** Listado del panel: todo, publicado o no. */
    List<Guia> findAllByOrderByPosicionAscTituloAsc();

    /**
     * `LEFT JOIN FETCH` sobre los pasos: el detalle los pinta siempre, y sin
     * esto cada guía dispara una consulta extra al recorrer la lista.
     */
    @Query("""
            SELECT g FROM Guia g
            LEFT JOIN FETCH g.pasos
            WHERE g.slug = :slug
            """)
    Optional<Guia> buscarPorSlugConPasos(@Param("slug") String slug);

    boolean existsBySlug(String slug);
}
