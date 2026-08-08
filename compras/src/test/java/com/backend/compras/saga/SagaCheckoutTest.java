package com.backend.compras.saga;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.backend.compras.saga.SagaCheckout.Estado;
import com.backend.compras.saga.SagaCheckout.Paso;

/**
 * La decisión de qué compensar depende del paso alcanzado. Si esto se
 * equivoca, o se libera stock que ya se cobró, o se deja bloqueado el que no.
 */
class SagaCheckoutTest {

    private SagaCheckout enPaso(Paso paso) {
        return SagaCheckout.builder()
                .referencia("sz-1-1-123")
                .usuarioId(1L)
                .metodoPagoId(1L)
                .total(new BigDecimal("100.00"))
                .paso(paso)
                .build();
    }

    @Test
    @DisplayName("antes de reservar no hay stock que liberar")
    void sinReservaNoHayNadaQueLiberar() {
        assertThat(enPaso(Paso.INICIO).tieneStockReservado()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = Paso.class, names = {
            "STOCK_RESERVADO", "PEDIDO_CREADO", "PREFERENCIA_CREADA", "PAGO_VERIFICADO" })
    @DisplayName("entre reservar y confirmar, la compensación debe liberar stock")
    void reservaViva(Paso paso) {
        assertThat(enPaso(paso).tieneStockReservado()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = Paso.class, names = { "STOCK_CONFIRMADO", "PEDIDO_PAGADO", "ENVIO_CREADO", "FIN" })
    @DisplayName("una vez confirmada la reserva ya no se libera: el stock está vendido")
    void reservaYaConfirmada(Paso paso) {
        assertThat(enPaso(paso).tieneStockReservado()).isFalse();
    }

    @Test
    @DisplayName("una saga terminada no se vuelve a compensar")
    void sagasTerminadas() {
        SagaCheckout saga = enPaso(Paso.FIN);

        saga.setEstado(Estado.COMPLETADA);
        assertThat(saga.estaTerminada()).isTrue();

        saga.setEstado(Estado.COMPENSADA);
        assertThat(saga.estaTerminada()).isTrue();

        saga.setEstado(Estado.FALLIDA);
        assertThat(saga.estaTerminada()).isTrue();

        saga.setEstado(Estado.ESPERANDO_PAGO);
        assertThat(saga.estaTerminada()).isFalse();
    }

    @Test
    @DisplayName("cada fallo incrementa el contador de intentos y recorta el mensaje")
    void registroDeErrores() {
        SagaCheckout saga = enPaso(Paso.STOCK_RESERVADO);

        saga.marcarError("catálogo no responde");
        assertThat(saga.getIntentos()).isEqualTo(1);

        saga.marcarError("x".repeat(900));
        assertThat(saga.getIntentos()).isEqualTo(2);
        // La columna admite 500: un mensaje largo no debe romper el guardado.
        assertThat(saga.getUltimoError()).hasSize(500);
    }
}
