package com.backend.compras.carrito;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CarritoRepository extends JpaRepository<Carrito, Long> {

    @Query("SELECT c FROM Carrito c LEFT JOIN FETCH c.items WHERE c.usuarioId = :usuarioId")
    Optional<Carrito> buscarConItems(@Param("usuarioId") Long usuarioId);

    Optional<Carrito> findByUsuarioId(Long usuarioId);
}
