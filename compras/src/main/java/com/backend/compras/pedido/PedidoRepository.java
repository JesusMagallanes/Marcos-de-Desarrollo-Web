package com.backend.compras.pedido;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @Query("""
            SELECT DISTINCT p FROM Pedido p
            LEFT JOIN FETCH p.detalles
            LEFT JOIN FETCH p.metodoPago
            WHERE p.usuarioId = :usuarioId
            ORDER BY p.creadoEn DESC
            """)
    List<Pedido> listarPorUsuario(@Param("usuarioId") Long usuarioId);

    @Query("""
            SELECT DISTINCT p FROM Pedido p
            LEFT JOIN FETCH p.detalles
            LEFT JOIN FETCH p.metodoPago
            ORDER BY p.creadoEn DESC
            """)
    List<Pedido> listarTodos();

    @Query("""
            SELECT DISTINCT p FROM Pedido p
            LEFT JOIN FETCH p.detalles
            LEFT JOIN FETCH p.metodoPago
            WHERE p.estado = :estado
            ORDER BY p.creadoEn DESC
            """)
    List<Pedido> listarPorEstado(@Param("estado") EstadoPedido estado);

    @Query("""
            SELECT p FROM Pedido p
            LEFT JOIN FETCH p.detalles
            LEFT JOIN FETCH p.metodoPago
            WHERE p.id = :id
            """)
    Optional<Pedido> buscarConDetalles(@Param("id") Long id);

    /** Idempotencia del webhook: un mismo pago no genera dos pedidos. */
    Optional<Pedido> findByPaymentId(String paymentId);

    boolean existsByPaymentId(String paymentId);

    /**
     * ¿Este usuario llegó a comprar este producto?
     *
     * <p>Lo pregunta `catalogo` antes de dejar valorar: sin esto, cualquiera con
     * una cuenta podía puntuar cualquier cosa sin haberla comprado nunca, que es
     * como se llenan de reseñas falsas las tiendas.
     *
     * <p>Cuentan los estados en los que el dinero ya se cobró. Un PENDIENTE es
     * una compra empezada y no pagada: quien la abandona no ha comprado nada. Un
     * CANCELADO tampoco, y ademas se le devolvio el dinero.
     */
    @Query("""
            SELECT COUNT(d) > 0 FROM Pedido p JOIN p.detalles d
            WHERE p.usuarioId = :usuarioId
              AND d.productoId = :productoId
              AND p.estado IN (com.backend.compras.pedido.EstadoPedido.PAGADO,
                               com.backend.compras.pedido.EstadoPedido.EN_TRANSITO,
                               com.backend.compras.pedido.EstadoPedido.ENTREGADO)
            """)
    boolean comproElProducto(@Param("usuarioId") Long usuarioId,
            @Param("productoId") Long productoId);
}
