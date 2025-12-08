package Pry_01.Web.de.Ventas.de.Computadoras.Dto.PedidoDTO;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class DetallePedidoDTO {
    private Long productoId;
    private String productoNombre;
    private Integer cantidad;
    private BigDecimal precioUnitario;
    private BigDecimal subtotal;
}
