package com.backend.compras.carrito;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CarritoItemRepository extends JpaRepository<CarritoItem, Long> {

    Optional<CarritoItem> findByCarritoIdAndProductoId(Long carritoId, Long productoId);

    /**
     * Se busca por id de item Y de carrito a la vez. Así un usuario no puede
     * borrar el item de otro pasando un id ajeno, que es lo que permitía
     * `eliminarItem(itemId)` en el monolito.
     */
    Optional<CarritoItem> findByIdAndCarritoId(Long id, Long carritoId);
}
