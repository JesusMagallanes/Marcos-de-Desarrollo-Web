package com.backend.compras.pedido;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Qué cuenta como una compra.
 *
 * <p>Eran una sola regla que decidía dos cosas: qué sale en «Mis compras» y
 * quién puede valorar un producto. El contra entrega las separó, porque ese
 * pedido hay que enseñárselo al comprador desde el primer momento y en cambio no
 * da derecho a opinar hasta que se cobre. Son dos conjuntos y las dos preguntas
 * se hacen aquí, para que nadie vuelva a juntarlos sin darse cuenta de lo que
 * eso implica.
 *
 * <p>Estas pruebas fijan las reglas en sí, no una consulta concreta, para que un
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
    @DisplayName("un contra entrega sin cobrar no da derecho a valorar")
    void confirmadoNoEsCompraTodavia() {
        // El pedido existe y va de camino, pero el dinero llega con el
        // repartidor: no se ha pagado ni se ha recibido nada de lo que opinar.
        assertThat(EstadoPedido.COMPRADOS).doesNotContain(EstadoPedido.CONFIRMADO);
    }

    @Test
    @DisplayName("...pero el comprador SÍ lo ve en sus compras desde el primer momento")
    void confirmadoSaleEnMisCompras() {
        // Es el pedido que acaba de hacer. Esconderlo hasta que se cobre lo
        // dejaría comprando a ciegas, sin nada que mirar después de comprar.
        assertThat(EstadoPedido.EN_MIS_COMPRAS).contains(EstadoPedido.CONFIRMADO);
    }

    @Test
    @DisplayName("lo que da derecho a valorar sale también en Mis compras")
    void loCobradoSiempreSeVe() {
        assertThat(EstadoPedido.EN_MIS_COMPRAS).containsAll(EstadoPedido.COMPRADOS);
    }

    @Test
    @DisplayName("ni un abandonado ni un cancelado salen en Mis compras")
    void loQueNoEsPedidoNoSeVe() {
        assertThat(EstadoPedido.EN_MIS_COMPRAS)
                .doesNotContain(EstadoPedido.PENDIENTE, EstadoPedido.CANCELADO);
    }

    @Test
    @DisplayName("todo estado está clasificado: no hay terreno de nadie")
    void ningunEstadoSinDecidir() {
        // Si mañana alguien añade REEMBOLSADO y no toca estas listas, aquí se ve.
        // La alternativa es que aparezca —o desaparezca— de «Mis compras» sin
        // que nadie lo haya decidido. Es lo que pasó al añadir CONFIRMADO, y por
        // eso esta prueba existe.
        for (EstadoPedido estado : EstadoPedido.values()) {
            boolean fueraDeTodo = estado == EstadoPedido.PENDIENTE || estado == EstadoPedido.CANCELADO;

            assertThat(EstadoPedido.EN_MIS_COMPRAS.contains(estado) ^ fueraDeTodo)
                    .as("El estado %s no está clasificado: ni sale en Mis compras ni se ha"
                            + " decidido que no debe salir", estado)
                    .isTrue();
        }
    }
}
