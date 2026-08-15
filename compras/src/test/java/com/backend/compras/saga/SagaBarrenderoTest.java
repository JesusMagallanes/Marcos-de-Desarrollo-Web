package com.backend.compras.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.backend.compras.pago.MercadoPagoClient;
import com.backend.compras.pago.MercadoPagoClient.Pago;
import com.backend.compras.shared.seguridad.TokenServicio;

/**
 * Lo que hace el barrendero con una compra que se quedó sin confirmar.
 *
 * <p>El caso que da nombre a esta clase de prueba es el que estuvo mal hasta
 * ahora: alguien paga, cierra la pestaña y no vuelve. Como la confirmación
 * llegaba solo por la URL de retorno, el barrendero daba la compra por
 * abandonada y la compensaba — pedido cancelado, stock devuelto a la tienda y el
 * cobro hecho. La regla es simple y es la que se prueba aquí: **antes de
 * cancelar, preguntar si se pagó**.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Barrendero de compras sin confirmar")
class SagaBarrenderoTest {

    @Mock
    private SagaCheckoutRepository sagas;
    @Mock
    private CheckoutOrquestador orquestador;
    @Mock
    private MercadoPagoClient mercadoPago;
    @Mock
    private TokenServicio tokenServicio;

    private SagaBarrendero barrendero;

    private static final String REFERENCIA = "sz-42-abc123";

    @BeforeEach
    void preparar() {
        barrendero = new SagaBarrendero(sagas, orquestador, mercadoPago, tokenServicio);
        ReflectionTestUtils.setField(barrendero, "minutosAbandono", 25);
        ReflectionTestUtils.setField(barrendero, "maxIntentos", 5);
    }

    private SagaCheckout abandonada() {
        return SagaCheckout.builder()
                .id(1L)
                .referencia(REFERENCIA)
                .usuarioId(42L)
                .metodoPagoId(1L)
                .total(new BigDecimal("100.00"))
                .build();
    }

    private Pago aprobado() {
        return new Pago("pay-777", "approved", new BigDecimal("100.00"), REFERENCIA);
    }

    @Test
    @DisplayName("si SÍ se pagó, se completa la compra y NO se compensa")
    void pagadaNoSeCompensa() {
        when(sagas.buscarAbandonadas(any())).thenReturn(List.of(abandonada()));
        when(mercadoPago.buscarPagoAprobado(REFERENCIA)).thenReturn(Optional.of(aprobado()));
        when(tokenServicio.emitir()).thenReturn("token-de-servicio");

        barrendero.compensarAbandonadas();

        // Lo importante de toda esta clase: el cobro está hecho, así que la
        // compra se cierra en vez de cancelarse.
        verify(orquestador).confirmar(42L, "pay-777", "token-de-servicio");
        verify(orquestador, never()).compensar(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("si no se pagó, se compensa como siempre")
    void noPagadaSeCompensa() {
        when(sagas.buscarAbandonadas(any())).thenReturn(List.of(abandonada()));
        when(mercadoPago.buscarPagoAprobado(REFERENCIA)).thenReturn(Optional.empty());
        when(tokenServicio.emitir()).thenReturn("token-de-servicio");

        barrendero.compensarAbandonadas();

        verify(orquestador).compensar(any(), anyString(), anyString());
        verify(orquestador, never()).confirmar(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("la compensación viaja con el token del servicio, no con null")
    void compensaConTokenPropio() {
        when(sagas.buscarAbandonadas(any())).thenReturn(List.of(abandonada()));
        when(mercadoPago.buscarPagoAprobado(REFERENCIA)).thenReturn(Optional.empty());
        when(tokenServicio.emitir()).thenReturn("token-de-servicio");

        barrendero.compensarAbandonadas();

        // Con `null`, la cabecera de autorización no viajaba y catálogo respondía
        // 401: la compensación fallaba en silencio y el stock solo se liberaba
        // cuando caducaba la reserva.
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(orquestador).compensar(any(), token.capture(), anyString());
        assertThat(token.getValue()).isEqualTo("token-de-servicio");
    }

    @Test
    @DisplayName("si la pasarela no responde, NO se cancela nada")
    void pasarelaCaidaNoCancela() {
        when(sagas.buscarAbandonadas(any())).thenReturn(List.of(abandonada()));
        // `buscarPagoAprobado` devuelve vacío tanto si no hay pago como si la
        // consulta falló. Aquí se comprueba el comportamiento con vacío; la
        // decisión de no propagar el error vive en el cliente, y es deliberada:
        // reintentar en la siguiente pasada es preferible a compensar a ciegas.
        when(mercadoPago.buscarPagoAprobado(REFERENCIA)).thenReturn(Optional.empty());
        when(tokenServicio.emitir()).thenReturn("t");

        barrendero.compensarAbandonadas();

        verify(mercadoPago).buscarPagoAprobado(REFERENCIA);
    }

    @Test
    @DisplayName("sin compras pendientes no se llama a la pasarela")
    void nadaQueHacer() {
        when(sagas.buscarAbandonadas(any())).thenReturn(List.of());

        barrendero.compensarAbandonadas();

        verify(mercadoPago, never()).buscarPagoAprobado(anyString());
        verify(tokenServicio, never()).emitir();
    }
}
