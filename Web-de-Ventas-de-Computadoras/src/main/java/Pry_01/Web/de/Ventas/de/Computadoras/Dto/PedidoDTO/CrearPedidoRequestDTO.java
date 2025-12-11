package Pry_01.Web.de.Ventas.de.Computadoras.Dto.PedidoDTO;

import lombok.Data;

@Data
public class CrearPedidoRequestDTO {
    private Long usuarioId;
    private Long metodoPagoId;
}
