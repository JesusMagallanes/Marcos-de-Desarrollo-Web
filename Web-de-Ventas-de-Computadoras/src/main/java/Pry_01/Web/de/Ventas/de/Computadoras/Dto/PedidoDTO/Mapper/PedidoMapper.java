package Pry_01.Web.de.Ventas.de.Computadoras.Dto.PedidoDTO.Mapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import Pry_01.Web.de.Ventas.de.Computadoras.Dto.PedidoDTO.DetallePedidoDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.PedidoDTO.PedidoResponseDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.DetallePedidoModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.PedidoModel;

@Component
public class PedidoMapper {

    public PedidoResponseDTO toDto(PedidoModel pedido) {
        PedidoResponseDTO dto = new PedidoResponseDTO();

        dto.setId(pedido.getId());
        dto.setUsuarioId(pedido.getUsuario().getId());
        dto.setUsuarioNombre(pedido.getUsuario().getName());
        dto.setMetodoPagoId(pedido.getMetodoPago().getId());
        dto.setMetodoPagoNombre(pedido.getMetodoPago().getName());
        dto.setTotal(pedido.getTotal());
        dto.setEstado(pedido.getEstado().name());
        dto.setCreadoEn(pedido.getCreadoEn());

        List<DetallePedidoDTO> detalles = pedido.getDetalles()
                .stream()
                .map(this::toDetalleDto)
                .collect(Collectors.toList());

        dto.setDetalles(detalles);

        return dto;
    }

    public DetallePedidoDTO toDetalleDto(DetallePedidoModel detalle) {
        DetallePedidoDTO dto = new DetallePedidoDTO();

        dto.setProductoId(detalle.getProducto().getId());
        dto.setProductoNombre(detalle.getProducto().getName());
        dto.setCantidad(detalle.getCantidad());
        dto.setPrecioUnitario(detalle.getPrecioUnitario());
        dto.setSubtotal(detalle.getPrecioUnitario().multiply(BigDecimal.valueOf(detalle.getCantidad())));

        return dto;
    }
}

