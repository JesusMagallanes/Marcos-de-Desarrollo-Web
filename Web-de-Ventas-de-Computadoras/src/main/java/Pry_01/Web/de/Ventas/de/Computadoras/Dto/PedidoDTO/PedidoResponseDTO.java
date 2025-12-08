package Pry_01.Web.de.Ventas.de.Computadoras.Dto.PedidoDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

@Data
public class PedidoResponseDTO {
    private Long id;
    private Long usuarioId;
    private String usuarioNombre;
    private Long metodoPagoId;
    private String metodoPagoNombre;
    private BigDecimal total;
    private String estado;
    private LocalDateTime creadoEn;
    private List<DetallePedidoDTO> detalles;
}
