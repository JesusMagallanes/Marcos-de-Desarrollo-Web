package com.backend.compras.envio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La máquina de estados del envío impide, por ejemplo, volver a PENDIENTE
 * un paquete ya ENTREGADO.
 */
class EstadoEnvioTest {

    @Test
    @DisplayName("un envío pendiente solo puede pasar a en tránsito")
    void desdePendiente() {
        assertThat(EstadoEnvio.PENDIENTE.puedePasarA(EstadoEnvio.EN_TRANSITO)).isTrue();
        assertThat(EstadoEnvio.PENDIENTE.puedePasarA(EstadoEnvio.ENTREGADO)).isFalse();
        assertThat(EstadoEnvio.PENDIENTE.puedePasarA(EstadoEnvio.PENDIENTE)).isFalse();
    }

    @Test
    @DisplayName("un envío en tránsito solo puede marcarse como entregado")
    void desdeEnTransito() {
        assertThat(EstadoEnvio.EN_TRANSITO.puedePasarA(EstadoEnvio.ENTREGADO)).isTrue();
        assertThat(EstadoEnvio.EN_TRANSITO.puedePasarA(EstadoEnvio.PENDIENTE)).isFalse();
        assertThat(EstadoEnvio.EN_TRANSITO.puedePasarA(EstadoEnvio.EN_TRANSITO)).isFalse();
    }

    @Test
    @DisplayName("entregado es estado final: no acepta ninguna transición")
    void desdeEntregado() {
        for (EstadoEnvio destino : EstadoEnvio.values()) {
            assertThat(EstadoEnvio.ENTREGADO.puedePasarA(destino))
                    .as("ENTREGADO -> %s", destino).isFalse();
        }
    }
}
