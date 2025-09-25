package Pry_01.Web.de.Ventas.de.Computadoras.Dto;

import java.time.LocalDateTime;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.EstadoEnvio;

public class EnviosDto {

    private Long id;

    @NotBlank(message = "La dirección de envío no puede estar vacía.")
    private String direccion;

    @NotNull(message = "El envío debe estar asociado a un pedido.")
    private Long pedidoId;

    public Long getPedidoId() {
        return pedidoId;
    }

    public void setPedidoId(Long pedidoId) {
        this.pedidoId = pedidoId;
    }

    @NotNull(message = "El estado de envío es obligatorio")
    private EstadoEnvio estadoEnvio;

    @NotNull(message = "Debe especificarse una fecha de envío programada.")
    @FutureOrPresent(message = "La fecha de envío no puede estar en el pasado.")
    private LocalDateTime fechaEnvioProgramado;

    private LocalDateTime fechaEnvioEntregado;

    public EnviosDto() {
    }

    public EnviosDto(Long id, String direccion, EstadoEnvio estadoEnvio, Long pedidoId,
            LocalDateTime fechaEnvioProgramado, LocalDateTime fechaEnvioEntregado) {
        this.id = id;
        this.direccion = direccion;
        this.pedidoId = pedidoId;
        this.estadoEnvio = estadoEnvio;
        this.fechaEnvioProgramado = fechaEnvioProgramado;
        this.fechaEnvioEntregado = fechaEnvioEntregado;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public EstadoEnvio getEstadoEnvio() {
        return estadoEnvio;
    }

    public void setEstadoEnvio(EstadoEnvio estadoEnvio) {
        this.estadoEnvio = estadoEnvio;
    }

    public LocalDateTime getFechaEnvioProgramado() {
        return fechaEnvioProgramado;
    }

    public void setFechaEnvioProgramado(LocalDateTime fechaEnvioProgramado) {
        this.fechaEnvioProgramado = fechaEnvioProgramado;
    }

    public LocalDateTime getFechaEnvioEntregado() {
        return fechaEnvioEntregado;
    }

    public void setFechaEnvioEntregado(LocalDateTime fechaEnvioEntregado) {
        this.fechaEnvioEntregado = fechaEnvioEntregado;
    }

}