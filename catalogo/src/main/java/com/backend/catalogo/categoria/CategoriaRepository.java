package com.backend.catalogo.categoria;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    Optional<Categoria> findBySlug(String slug);

    boolean existsByName(String name);

    /** Cuantas cuelgan de esta. Cero es condicion para poder borrarla. */
    long countByPadreId(Long padreId);

    boolean existsBySlug(String slug);

    List<Categoria> findAllByOrderByNameAsc();
}
