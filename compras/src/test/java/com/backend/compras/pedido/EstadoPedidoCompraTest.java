package com.backend.compras.pedido;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Qué cuenta como una compra.
 *
 * <p>La regla decide dos cosas que a primera vista no tienen que ver: qué sale
 * en «Mis compras» y quién puede valorar un producto. Estaba escrita dos veces
 * —una en cada consulta— y separarlas significaba que alguien acabara pudiendo
 * valorar algo que no compró, o que el comprador no viera lo que sí compró.
 *
 * <p>Estas pruebas fijan la regla en sí, no una consulta concreta, para que un
 * estado nuevo obligue a decidir de qué lado cae.
 */
@DisplayName("Qué cuenta como una compra")
class EstadoPedidoCompraTest {

    @ParameterizedTest
    @EnumSource(value = EstadoPedido.class, names = { "PAGADO", "EN_TRANSITO", "ENTREGADO" })
    @DisplayName("cobrado y en camino: es una compra")
    void loCobradoEsCompra(EstadoPedido estado) {
        assertThat(EstadoPedido.COMPRADOS).contains(estado);
    }

    @Test
    @DisplayName("un checkout abandonado NO es una compra")
    void pendienteNoEsCompra() {
        // PENDIENTE es una compra empezada y sin pagar. Salía en «Mis compras» y
        // el comprador veía un pedido de algo que nunca compró.
        assertThat(EstadoPedido.COMPRADOS).doesNotContain(EstadoPedido.PENDIENTE);
    }

    @Test
    @DisplayName("un pedido cancelado tampoco")
    void canceladoNoEsCompra() {
        // Si llegó a cobrarse, se le devolvió el dinero; y la mayoría son
        // checkouts que la saga canceló porque el pago no llegó nunca.
        assertThat(EstadoPedido.COMPRADOS).doesNotContain(EstadoPedido.CANCELADO);
    }

    @Test
    @DisplayName("todo estado está clasificado: no hay terreno de nadie")
    void ningunEstadoSinDecidir() {
        // Si mañana alguien añade REEMBOLSADO y no toca esta lista, aquí se ve.
        // La alternativa es que aparezca —o desaparezca— de «Mis compras» sin
        // que nadie lo haya decidido.
        for (EstadoPedido estado : EstadoPedido.values()) {
            boolean esCompra = EstadoPedido.COMPRADOS.contains(estado);
            boolean noEsCompra = estado == EstadoPedido.PENDIENTE || estado == EstadoPedido.CANCELADO;

            assertThat(esCompra ^ noEsCompra)
                    .as("El estado %s no está clasificado como compra ni como no-compra", estado)
                    .isTrue();
        }
    }
}
