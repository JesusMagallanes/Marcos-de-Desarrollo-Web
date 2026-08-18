package com.backend.compras.saga;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import com.backend.compras.carrito.Carrito;
import com.backend.compras.carrito.CarritoRepository;
import com.backend.compras.carrito.CarritoService;
import com.backend.compras.envio.EnvioService;
import com.backend.compras.metodopago.MetodoPago;
import com.backend.compras.pago.MercadoPagoClient;
import com.backend.compras.pago.MercadoPagoClient.Pago;
import com.backend.compras.pago.dto.PagoDtos.PreferenciaRequest;
import com.backend.compras.pedido.EstadoPedido;
import com.backend.compras.pedido.Pedido;
import com.backend.compras.pedido.PedidoRepository;
import com.backend.compras.pedido.PedidoService;
import com.backend.compras.saga.SagaCheckout.Estado;
import com.backend.compras.saga.SagaCheckout.Paso;
import com.backend.compras.shared.catalogo.CatalogoClient;
import com.backend.compras.shared.error.ConflictoException;
import com.backend.compras.shared.metricas.MetricasSeguridad;

/**
 * La regla que protege el dinero del comprador: <b>nunca se tira una compra sin
 * preguntar antes si se pagó</b>.
 *
 * <p>El barrendero ya la cumplía, pero había un segundo camino que no: el propio
 * checkout. Quien paga y no vuelve a la tienda —porque cierra la pestaña, o
 * porque MercadoPago se quedó sin botón de retorno— deja una compra viva con el
 * cobro hecho. Si entonces vuelve al carrito y le da a pagar otra vez, el
 * checkout compensaba esa compra sin mirar: pedido cancelado, stock devuelto y el
 * dinero cobrado. Y como COMPENSADA es un estado final, el barrendero ya no la
 * volvía a mirar, así que el pago se perdía para siempre.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Orquestador del checkout")
class CheckoutOrquestadorTest {

    @Mock
    private ObjectProvider<CheckoutOrquestador> proxia;
    @Mock
    private SagaCheckoutRepository sagas;
    @Mock
    private CarritoRepository carritos;
    @Mock
    private CarritoService carritoService;
    @Mock
    private PedidoRepository pedidos;
    @Mock
    private PedidoService pedidoService;
    @Mock
    private CatalogoClient catalogo;
    @Mock
    private MercadoPagoClient mercadoPago;
    @Mock
    private EnvioService envioService;
    @Mock
    private MetricasSeguridad metricas;

    private CheckoutOrquestador orquestador;

    private static final Long USUARIO = 42L;
    private static final String REFERENCIA = "sz-42-1-1723456789000";
    private static final String TOKEN = "token-del-usuario";

    @BeforeEach
    void preparar() {
        orquestador = new CheckoutOrquestador(proxia, sagas, carritos, carritoService,
                pedidos, pedidoService, catalogo, mercadoPago, envioService, metricas);
        // El propio orquestador visto por el proxy: en producción lo da Spring.
        lenient().when(proxia.getObject()).thenReturn(orquestador);
    }

    private SagaCheckout previaViva() {
        return SagaCheckout.builder()
                .id(7L)
                .referencia(REFERENCIA)
                .usuarioId(USUARIO)
                .metodoPagoId(1L)
                .pedidoId(90L)
                .total(new BigDecimal("100.00"))
                .estado(Estado.ESPERANDO_PAGO)
                .paso(Paso.PREFERENCIA_CREADA)
                .build();
    }

    private PreferenciaRequest peticion() {
        return new PreferenciaRequest(1L, "Av. Los Próceres 1420", null, "987654321", null, null);
    }

    private Pedido pedidoPagable() {
        return Pedido.builder()
                .id(90L)
                .usuarioId(USUARIO)
                .total(new BigDecimal("100.00"))
                .estado(EstadoPedido.PENDIENTE)
                .metodoPago(MetodoPago.builder().id(1L).name("MercadoPago").build())
                .detalles(new ArrayList<>())
                .build();
    }

    @Test
    @DisplayName("reintentar el checkout NO tira una compra que sí se pagó")
    void reintentoNoDescartaUnPagoCobrado() {
        SagaCheckout previa = previaViva();
        when(sagas.buscarActivasDeUsuario(USUARIO)).thenReturn(List.of(previa));

        // La pasarela dice que esa compra sí se cobró.
        Pago pago = new Pago("137477397026", "approved", new BigDecimal("100.00"), REFERENCIA);
        when(mercadoPago.buscarPagoAprobado(REFERENCIA)).thenReturn(Optional.of(pago));

        // …y esto es el cierre de esa compra, por donde sigue la conciliación.
        when(sagas.findByPaymentId(pago.id())).thenReturn(Optional.empty());
        when(mercadoPago.consultarPago(pago.id())).thenReturn(pago);
        when(sagas.findByReferencia(REFERENCIA)).thenReturn(Optional.of(previa));
        when(pedidos.buscarConDetalles(90L)).thenReturn(Optional.of(pedidoPagable()));
        when(carritos.buscarConItems(USUARIO)).thenReturn(Optional.of(new Carrito()));

        assertThatThrownBy(() -> orquestador.iniciar(USUARIO, peticion(), TOKEN))
                .isInstanceOf(ConflictoException.class)
                // Al comprador hay que decírselo, o pagará dos veces lo mismo.
                .hasMessageContaining("sí se completó");

        // Lo que NO puede pasar bajo ningún concepto.
        verify(catalogo, never()).liberarReserva(anyString(), anyString());
        verify(mercadoPago, never()).crearPreferencia(anyString(), any(), anyString());

        // Y la compra anterior queda cerrada, no cancelada.
        verify(catalogo).confirmarReserva(TOKEN, REFERENCIA);
    }

    @Test
    @DisplayName("si la compra anterior no se pagó, se compensa como antes")
    void reintentoCompensaLoNoPagado() {
        SagaCheckout previa = previaViva();
        when(sagas.buscarActivasDeUsuario(USUARIO)).thenReturn(List.of(previa));
        when(mercadoPago.buscarPagoAprobado(REFERENCIA)).thenReturn(Optional.empty());

        // El carrito vacío corta el checkout justo después de compensar: lo que
        // se prueba aquí es la decisión sobre la compra anterior, no el resto.
        when(carritos.buscarConItems(USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orquestador.iniciar(USUARIO, peticion(), TOKEN))
                .isInstanceOf(ConflictoException.class);

        verify(catalogo).liberarReserva(TOKEN, REFERENCIA);
    }

    @Test
    @DisplayName("la compensación pasa por el proxy, o se desharía con el error")
    void compensacionPorElProxy() {
        SagaCheckout previa = previaViva();
        when(sagas.buscarActivasDeUsuario(USUARIO)).thenReturn(List.of(previa));
        when(mercadoPago.buscarPagoAprobado(REFERENCIA)).thenReturn(Optional.empty());
        when(carritos.buscarConItems(USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orquestador.iniciar(USUARIO, peticion(), TOKEN))
                .isInstanceOf(ConflictoException.class);

        // Llamar a `compensar` directamente saltaría su @Transactional(REQUIRES_NEW)
        // y la compensación se desharía junto con la transacción que está fallando.
        verify(proxia).getObject();
    }
}
