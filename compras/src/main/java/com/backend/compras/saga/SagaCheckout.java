package com.backend.compras.saga;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Estado persistido de una compra en curso. */
@Entity
@Table(name = "saga_checkout")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SagaCheckout {

    public enum Estado {
        INICIADA,
        // Reserva y pedido creados; se espera que el usuario pague.
        ESPERANDO_PAGO,
        COMPLETADA,
        // Falló algo: se están deshaciendo los pasos ya dados.
        COMPENSANDO,
        COMPENSADA,
        // La compensación también falló: requiere intervención manual.
        FALLIDA
    }

    /** Pasos en orden. La compensación los recorre al revés. */
    public enum Paso {
        INICIO,
        STOCK_RESERVADO,
        PEDIDO_CREADO,
        PREFERENCIA_CREADA,
        PAGO_VERIFICADO,
        STOCK_CONFIRMADO,
        PEDIDO_PAGADO,
        ENVIO_CREADO,
        FIN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80, unique = true)
    private String referencia;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "metodopago_id", nullable = false)
    private Long metodoPagoId;

    @Column(name = "pedido_id")
    private Long pedidoId;

    /*
     * Destino del pedido, capturado al iniciar el checkout. Viaja con la saga
     * porque entre el inicio y la confirmación el comprador se va a MercadoPago:
     * cuando vuelve (o cuando llega el webhook) ya no hay formulario del que
     * leerlo, y el envío se creaba con un "Por confirmar" que no servía a nadie.
     */
    @Column(name = "direccion_envio", length = 200)
    private String direccionEnvio;

    @Column(name = "referencia_envio", length = 200)
    private String referenciaEnvio;

    @Column(name = "telefono_contacto", length = 9)
    private String telefonoContacto;

    /** Coordenadas del punto de entrega, si el comprador las compartió. */
    @Column(precision = 9, scale = 6)
    private BigDecimal latitud;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitud;

    @Column(name = "payment_id", length = 100)
    private String paymentId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private Estado estado = Estado.INICIADA;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private Paso paso = Paso.INICIO;

    @Column(nullable = false)
    @Builder.Default
    private Integer intentos = 0;

    @Column(name = "ultimo_error", length = 500)
    private String ultimoError;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    /** Evita que dos hilos avancen la misma saga a la vez. */
    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    void alCrear() {
        LocalDateTime ahora = LocalDateTime.now();
        if (creadoEn == null) {
            creadoEn = ahora;
        }
        actualizadoEn = ahora;
    }

    @PreUpdate
    void alActualizar() {
        actualizadoEn = LocalDateTime.now();
    }

    public void avanzarA(Paso siguiente) {
        this.paso = siguiente;
    }

    public void marcarError(String mensaje) {
        this.ultimoError = mensaje == null ? null
                : mensaje.substring(0, Math.min(mensaje.length(), 500));
        this.intentos = this.intentos + 1;
    }

    /** ¿Se llegó a reservar stock? Determina si hay que liberarlo al compensar. */
    public boolean tieneStockReservado() {
        return paso.ordinal() >= Paso.STOCK_RESERVADO.ordinal()
                && paso.ordinal() < Paso.STOCK_CONFIRMADO.ordinal();
    }

    public boolean tienePedido() {
        return pedidoId != null;
    }

    public boolean estaTerminada() {
        return estado == Estado.COMPLETADA || estado == Estado.COMPENSADA || estado == Estado.FALLIDA;
    }
}
