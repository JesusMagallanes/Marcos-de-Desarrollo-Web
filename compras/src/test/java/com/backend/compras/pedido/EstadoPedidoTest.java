package com.backend.compras.pedido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La máquina de estados impide, por ejemplo, "reactivar" un pedido entregado o
 * cobrar dos veces uno ya pagado.
 */
class EstadoPedidoTest {

    @Test
    @DisplayName("un pedido pendiente solo puede cerrarse, confirmarse o cancelarse")
    void desdePendiente() {
        assertThat(EstadoPedido.PENDIENTE.puedePasarA(EstadoPedido.PAGADO)).isTrue();
        // La salida del checkout contra entrega: el pedido queda en firme sin
        // haberse cobrado.
        assertThat(EstadoPedido.PENDIENTE.puedePasarA(EstadoPedido.CONFIRMADO)).isTrue();
        assertThat(EstadoPedido.PENDIENTE.puedePasarA(EstadoPedido.CANCELADO)).isTrue();
        assertThat(EstadoPedido.PENDIENTE.puedePasarA(EstadoPedido.ENTREGADO)).isFalse();
        assertThat(EstadoPedido.PENDIENTE.puedePasarA(EstadoPedido.EN_TRANSITO)).isFalse();
    }

    @Test
    @DisplayName("un contra entrega se envía sin pasar por PAGADO: se cobra al entregar")
    void desdeConfirmado() {
        assertThat(EstadoPedido.CONFIRMADO.puedePasarA(EstadoPedido.EN_TRANSITO)).isTrue();
        assertThat(EstadoPedido.CONFIRMADO.puedePasarA(EstadoPedido.CANCELADO)).isTrue();
        // No se cobra por adelantado, así que no hay vuelta a PAGADO; y tampoco
        // se salta el envío.
        assertThat(EstadoPedido.CONFIRMADO.puedePasarA(EstadoPedido.PAGADO)).isFalse();
        assertThat(EstadoPedido.CONFIRMADO.puedePasarA(EstadoPedido.ENTREGADO)).isFalse();
    }

    @Test
    @DisplayName("no se puede saltar del pago a la entrega sin pasar por el envío")
    void desdePagado() {
        assertThat(EstadoPedido.PAGADO.puedePasarA(EstadoPedido.EN_TRANSITO)).isTrue();
        assertThat(EstadoPedido.PAGADO.puedePasarA(EstadoPedido.CANCELADO)).isTrue();
        assertThat(EstadoPedido.PAGADO.puedePasarA(EstadoPedido.ENTREGADO)).isFalse();
        assertThat(EstadoPedido.PAGADO.puedePasarA(EstadoPedido.PENDIENTE)).isFalse();
    }

    @Test
    @DisplayName("entregado y cancelado son estados finales")
    void estadosFinales() {
        for (EstadoPedido destino : EstadoPedido.values()) {
            assertThat(EstadoPedido.ENTREGADO.puedePasarA(destino))
                    .as("ENTREGADO -> %s", destino).isFalse();
            assertThat(EstadoPedido.CANCELADO.puedePasarA(destino))
                    .as("CANCELADO -> %s", destino).isFalse();
        }
    }

    @Test
    @DisplayName("la entidad rechaza una transición inválida")
    void laEntidadProtegeSuEstado() {
        Pedido pedido = Pedido.builder()
                .usuarioId(1L)
                .total(new BigDecimal("50.00"))
                .estado(EstadoPedido.PENDIENTE)
                .build();

        assertThatThrownBy(() -> pedido.cambiarEstado(EstadoPedido.ENTREGADO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDIENTE");

        pedido.cambiarEstado(EstadoPedido.PAGADO);
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.PAGADO);
    }
}
