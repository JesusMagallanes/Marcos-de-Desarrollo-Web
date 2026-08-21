package com.backend.compras.pedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.backend.compras.metodopago.MetodoPago;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pedido")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Id plano: el usuario vive en el servicio `usuarios`. */
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    /** Sí es relación real: metodopago está en este mismo esquema. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "metodopago_id", nullable = false)
    private MetodoPago metodoPago;

    /*
     * De qué se compone el total. Se guardan los tres y no solo la suma: un
     * pedido es un documento de lo que se le cobró al comprador, y deducir el
     * envío restando convertiría cualquier descuadre en un dato inventado con
     * pinta de bueno.
     */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "costo_envio", nullable = false, precision = 12, scale = 2)
    private BigDecimal costoEnvio;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoPedido estado = EstadoPedido.PENDIENTE;

    /** Referencia del pago en la pasarela; con índice único evita duplicados. */
    @Column(name = "payment_id", length = 100)
    private String paymentId;

    /** Enlace con la saga que lo creó, para conciliar y compensar. */
    @Column(name = "referencia_saga", length = 80)
    private String referenciaSaga;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DetallePedido> detalles = new ArrayList<>();

    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }

    public void agregarDetalle(DetallePedido detalle) {
        detalles.add(detalle);
        detalle.setPedido(this);
    }

    public void cambiarEstado(EstadoPedido siguiente) {
        if (!estado.puedePasarA(siguiente)) {
            throw new IllegalStateException(
                    "No se puede pasar de " + estado + " a " + siguiente);
        }
        this.estado = siguiente;
    }
}
