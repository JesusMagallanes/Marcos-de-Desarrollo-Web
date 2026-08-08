package com.backend.compras.pedido.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.backend.compras.pedido.DetallePedido;
import com.backend.compras.pedido.EstadoPedido;
import com.backend.compras.pedido.Pedido;

import jakarta.validation.constraints.NotNull;

public final class PedidoDtos {

    private PedidoDtos() {
    }

    public record DetalleResponse(
            Long productoId,
            String productoNombre,
            String imagen,
            Integer cantidad,
            BigDecimal precioUnitario,
            BigDecimal total) {

        public static DetalleResponse desde(DetallePedido d) {
            return new DetalleResponse(
                    d.getProductoId(),
                    d.getProductoNombre(),
                    d.getProductoImagen(),
                    d.getCantidad(),
                    d.getPrecioUnitario(),
                    d.getTotal());
        }
    }

    public record PedidoResponse(
            Long id,
            Long usuarioId,
            LocalDateTime fecha,
            EstadoPedido estado,
            BigDecimal total,
            String metodoPago,
            List<DetalleResponse> detalles) {

        public static PedidoResponse desde(Pedido p) {
            return new PedidoResponse(
                    p.getId(),
                    p.getUsuarioId(),
                    p.getCreadoEn(),
                    p.getEstado(),
                    p.getTotal(),
                    p.getMetodoPago().getName(),
                    p.getDetalles().stream().map(DetalleResponse::desde).toList());
        }
    }

    public record CambioEstado(@NotNull EstadoPedido estado) {
    }
}
