package Pry_01.Web.de.Ventas.de.Computadoras.Dto;

import java.math.BigDecimal;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.EstadoPedido;
import jakarta.validation.constraints.DecimalMin;

public class PedidoDto {
    private Long id;

    private Long usuarioId;

    private Long metodoPagoId;

    @DecimalMin(value = "0.01", message = "El total debe ser mayor que 0")
    private BigDecimal total;

    private EstadoPedido estado;

    public PedidoDto() {
    }

    public PedidoDto(Long id,Long usuarioId, Long metodoPagoId, BigDecimal total, EstadoPedido estado) {
        this.id=id;
        this.usuarioId = usuarioId;
        this.metodoPagoId = metodoPagoId;
        this.total = total;
        this.estado = estado;
    }

     public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public Long getMetodoPagoId() {
        return metodoPagoId;
    }

    public void setMetodoPagoId(Long metodoPagoId) {
        this.metodoPagoId = metodoPagoId;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public EstadoPedido getEstado() {
        return estado;
    }

    public void setEstado(EstadoPedido estado) {
        this.estado = estado;
    }
}
