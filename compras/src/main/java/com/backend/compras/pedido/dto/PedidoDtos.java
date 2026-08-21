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
            /**
             * El número que ve el comprador, y el que dirá por teléfono si llama.
             *
             * <p>Se enseñaba el id de la tabla en crudo —«Pedido #7»—, que no
             * parece un número de pedido, cambia de longitud según cuántas
             * compras lleve la tienda y de paso le dice a cualquiera cuántas
             * son. El id sigue siendo la clave con la que trabaja la API; esto
             * es solo cómo se llama el pedido de cara afuera.
             */
            String numero,
            Long usuarioId,
            LocalDateTime fecha,
            EstadoPedido estado,
            /* El desglose, para que el detalle de la compra cuadre: las líneas
             * suman el subtotal y el envío es la diferencia con el total. */
            BigDecimal subtotal,
            BigDecimal costoEnvio,
            BigDecimal total,
            String metodoPago,
            List<DetalleResponse> detalles) {

        public static PedidoResponse desde(Pedido p) {
            return new PedidoResponse(
                    p.getId(),
                    numeroDe(p.getId()),
                    p.getUsuarioId(),
                    p.getCreadoEn(),
                    p.getEstado(),
                    p.getSubtotal(),
                    p.getCostoEnvio(),
                    p.getTotal(),
                    p.getMetodoPago().getName(),
                    p.getDetalles().stream().map(DetalleResponse::desde).toList());
        }

        /**
         * {@code SZ-000042}. Se compone aquí, en el único sitio donde se
         * construye la respuesta, para que el número que ve el comprador, el que
         * aparece en el panel y el que se diga por teléfono sean el mismo.
         */
        static String numeroDe(Long id) {
            return id == null ? "" : "SZ-%06d".formatted(id);
        }
    }

    public record CambioEstado(@NotNull EstadoPedido estado) {
    }
}
