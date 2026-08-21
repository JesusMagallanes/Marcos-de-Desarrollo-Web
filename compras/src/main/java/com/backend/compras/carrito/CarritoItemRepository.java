package com.backend.compras.carrito;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CarritoItemRepository extends JpaRepository<CarritoItem, Long> {

    Optional<CarritoItem> findByCarritoIdAndProductoId(Long carritoId, Long productoId);

    /**
     * Se busca por id de item Y de carrito a la vez. Así un usuario no puede
     * borrar el item de otro pasando un id ajeno, que es lo que permitía
     * `eliminarItem(itemId)` en el monolito.
     */
    Optional<CarritoItem> findByIdAndCarritoId(Long id, Long carritoId);

    /**
     * Borra la línea y dice CUÁNTAS filas ha borrado.
     *
     * <p>Ese número es lo que faltaba. El borrado se hacía quitando el ítem de
     * la colección y confiando en el {@code orphanRemoval} de JPA, que no
     * informa de nada: si el DELETE no llegaba a tocar ninguna fila —por una
     * política de Row Level Security que no encaja, por una colección que no era
     * la que se estaba mirando— el endpoint devolvía 200 con un carrito de
     * aspecto normal y el producto seguía ahí al recargar. Es el mismo fallo
     * mudo que ya documenta V4__row_level_security.sql para las migraciones:
     * cero filas afectadas y nadie se entera.
     *
     * <p>Sigue filtrando por carrito además de por id, para que un id ajeno no
     * borre la línea de otro.
     */
    @Modifying
    @Query("DELETE FROM CarritoItem i WHERE i.id = :itemId AND i.carrito.id = :carritoId")
    int borrarDelCarrito(@Param("itemId") Long itemId, @Param("carritoId") Long carritoId);

    /** Vacía el carrito entero. Devuelve cuántas líneas se llevó por delante. */
    @Modifying
    @Query("DELETE FROM CarritoItem i WHERE i.carrito.id = :carritoId")
    int borrarTodoElCarrito(@Param("carritoId") Long carritoId);
}
