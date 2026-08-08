package com.backend.compras.envio;

import java.time.LocalDateTime;

import com.backend.compras.pedido.Pedido;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Envío mantiene la relación real con Pedido porque ambos viven en el esquema
 * `compras`. Ese fue el motivo de dejarlos juntos en el mismo servicio.
 */
@Entity
@Table(name = "envios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Envio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false, unique = true)
    private Pedido pedido;

    @Column(nullable = false, length = 200)
    private String direccion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_envio", nullable = false, length = 20)
    @Builder.Default
    private EstadoEnvio estadoEnvio = EstadoEnvio.PENDIENTE;

    @Column(name = "fecha_envio_programado")
    private LocalDateTime fechaEnvioProgramado;

    @Column(name = "fecha_envio_entregado")
    private LocalDateTime fechaEnvioEntregado;

    public void marcarEnTransito() {
        this.estadoEnvio = EstadoEnvio.EN_TRANSITO;
        this.fechaEnvioProgramado = LocalDateTime.now();
    }

    public void marcarEntregado() {
        this.estadoEnvio = EstadoEnvio.ENTREGADO;
        this.fechaEnvioEntregado = LocalDateTime.now();
    }
}
