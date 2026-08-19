package com.backend.compras.envio;

import java.math.BigDecimal;
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

    /** Piso, referencia o indicación para el repartidor. Opcional. */
    @Column(length = 200)
    private String referencia;

    /** Para avisar de la entrega. Sale del checkout, no del perfil: quien compra
     *  puede estar mandando el pedido a otra persona. */
    @Column(name = "telefono_contacto", length = 9)
    private String telefonoContacto;

    /*
     * La direccion, en partes.
     *
     * `direccionEnvio` sigue siendo la linea que se imprime en la etiqueta, pero
     * ya no es lo unico: una cadena suelta no sirve para calcular un envio por
     * codigo postal, ni para agrupar reparto por distrito, ni para mandarsela a
     * la pasarela, que la quiere en campos separados.
     */
    @Column(length = 200)
    private String calle;

    @Column(length = 20)
    private String numero;

    @Column(name = "codigo_postal", length = 10)
    private String codigoPostal;

    @Column(length = 80)
    private String distrito;

    @Column(length = 80)
    private String provincia;

    @Column(length = 80)
    private String departamento;

    /** ISO 3166-1 alfa-2. */
    @Column(length = 2)
    private String pais;

    /** Quien recibe, que no siempre es quien compra: regalos, envios a la oficina. */
    @Column(name = "receptor_nombre", length = 120)
    private String receptorNombre;

    /*
     * Punto de entrega, si el comprador quiso compartirlo (Épica 3). Opcional a
     * propósito: nadie se queda sin comprar por no dar su posición, y sin ellas
     * el envío funciona igual, solo que sin el cálculo de distancia.
     */
    @Column(precision = 9, scale = 6)
    private BigDecimal latitud;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitud;

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
