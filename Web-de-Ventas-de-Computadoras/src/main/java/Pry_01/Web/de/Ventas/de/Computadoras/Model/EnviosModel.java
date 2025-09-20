package Pry_01.Web.de.Ventas.de.Computadoras.Model;

import java.time.LocalDateTime;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "envios")
public class EnviosModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "El envío debe estar asociado a un pedido.")
    @ManyToOne
    @JoinColumn(name = "pedido_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PedidoModel pedido;

    @NotBlank(message = "La dirección de envío no puede estar vacía.")
    @Column(nullable = false, length = 200)
    private String direccion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoEnvio estadoEnvio = EstadoEnvio.PENDIENTE;

    @NotNull(message = "Debe especificarse una fecha de envío programada.")
    @FutureOrPresent(message = "La fecha de envío no puede estar en el pasado.")
    @Column(nullable = false)
    private LocalDateTime fechaEnvioProgramado;

    @Column
    private LocalDateTime fechaEnvioEntregado;

    public EnviosModel() {}

    public EnviosModel(PedidoModel pedido, String direccion, LocalDateTime fechaEnvioProgramado) {
        this.pedido = pedido;
        this.direccion = direccion;
        this.estadoEnvio = EstadoEnvio.PENDIENTE;
        this.fechaEnvioProgramado = fechaEnvioProgramado;
    }

    public void marcarComoEntregado() {
        this.fechaEnvioEntregado = LocalDateTime.now();
        this.estadoEnvio = EstadoEnvio.ENTREGADO;
    }

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }

    public PedidoModel getPedido() {
        return pedido;
    }
    public void setPedido(PedidoModel pedido) {
        this.pedido = pedido;
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
