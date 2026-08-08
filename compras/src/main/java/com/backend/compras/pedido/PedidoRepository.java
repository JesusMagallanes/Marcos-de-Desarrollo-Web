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
}
