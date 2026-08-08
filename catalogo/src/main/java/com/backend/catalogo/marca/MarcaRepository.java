package com.backend.catalogo.marca;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MarcaRepository extends JpaRepository<Marca, Long> {

    boolean existsByName(String name);

    List<Marca> findByCategoriaIdOrderByNameAsc(Long categoriaId);

    List<Marca> findAllByOrderByNameAsc();
}
