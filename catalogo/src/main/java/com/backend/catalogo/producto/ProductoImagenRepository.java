package com.backend.catalogo.producto;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductoImagenRepository extends JpaRepository<ProductoImagen, Long> {

    void deleteByProductoId(Long productoId);

    /**
     * Las URL de galería que empiezan por un prefijo: las fotos alojadas aquí.
     *
     * <p>Lo usa la purga para saber qué ficheros siguen en uso. Es una lista de
     * cadenas y no de entidades a propósito: son unos cientos de URL, no hace
     * falta hidratar nada.
     */
    @Query("SELECT i.url FROM ProductoImagen i WHERE i.url LIKE CONCAT(:prefijo, '%')")
    List<String> urlsQueEmpiezanPor(@Param("prefijo") String prefijo);
}
