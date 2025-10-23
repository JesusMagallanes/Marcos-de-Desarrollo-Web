package Pry_01.Web.de.Ventas.de.Computadoras.Model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "pedido")
public class PedidoModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El pedido debe estar asociado a un usuario.")
    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioModel usuario;

    public UsuarioModel getUsuario() {
        return usuario;
    }

    public void setUsuario(UsuarioModel usuario) {
        this.usuario = usuario;
    }

    @NotBlank(message = "Debe seleccionarse un método de pago.")
    @ManyToOne
    @JoinColumn(name = "metodopago_id", nullable = false)
    private MetodoPagoModel metodoPago;

    @NotBlank(message = "El total no puede ser nulo.")
    @DecimalMin(value = "0.01", message = "El total debe ser mayor que 0.")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @NotNull(message = "El campo no puede estar vacío")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPedido estado = EstadoPedido.PENDIENTE;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    public PedidoModel() {}

    public PedidoModel(UsuarioModel usuario, MetodoPagoModel metodoPago, BigDecimal total, EstadoPedido estado) {
        this.usuario = usuario;
        this.metodoPago = metodoPago;
        this.total = total;
        this.estado = estado != null ? estado : EstadoPedido.PENDIENTE;
    }

    @PrePersist
    protected void onCreate() {
        this.creadoEn = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }

    

    public MetodoPagoModel getMetodoPago() {
        return metodoPago;
    }
    public void setMetodoPago(MetodoPagoModel metodoPago) {
        this.metodoPago = metodoPago;
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

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }
}
