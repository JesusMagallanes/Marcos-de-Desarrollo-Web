package com.backend.catalogo.producto;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductoImagenRepository extends JpaRepository<ProductoImagen, Long> {

    void deleteByProductoId(Long productoId);
}
