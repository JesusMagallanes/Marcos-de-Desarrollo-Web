package com.backend.compras.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.backend.compras.carrito.CarritoItem;
import com.backend.compras.carrito.CarritoRepository;
import com.backend.compras.carrito.CarritoService;
import com.backend.compras.envio.EnvioService;
import com.backend.compras.metodopago.MetodoPago;
import com.backend.compras.metodopago.MetodoPagoRepository;
import com.backend.compras.pago.MercadoPagoClient;
import com.backend.compras.pago.MercadoPagoClient.Pago;
import com.backend.compras.pago.dto.DireccionEntrega;
import com.backend.compras.pago.dto.PagoDtos.PreferenciaRequest;
import com.backend.compras.pedido.EstadoPedido;
import com.backend.compras.pedido.Pedido;
import com.backend.compras.pedido.PedidoRepository;
import com.backend.compras.pedido.PedidoService;
import com.backend.compras.saga.SagaCheckout.Estado;
import com.backend.compras.saga.SagaCheckout.Paso;
import com.backend.compras.shared.catalogo.CatalogoClient;
import com.backend.compras.shared.error.ConflictoException;
import com.backend.compras.shared.error.PagoEnCursoException;
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
    private MetodoPagoRepository metodosPago;
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
                pedidos, pedidoService, metodosPago, catalogo, mercadoPago, envioService, metricas);
        // El de siempre: MercadoPago. El contra entrega lo declara su prueba.
        lenient().when(metodosPago.findById(1L)).thenReturn(Optional.of(
                MetodoPago.builder().id(1L).name("MercadoPago").build()));
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

    private DireccionEntrega entrega() {
        return new DireccionEntrega(
                "Av. Los Próceres", "1420", "Piso 4", "15074",
                "Miraflores", "Lima", "Lima", "PE",
                "Ana Vega Ríos", "987654321", null, null);
    }

    private PreferenciaRequest peticion() {
        return new PreferenciaRequest(1L, entrega());
    }

    private Carrito carritoConUnItem() {
        Carrito carrito = Carrito.builder().id(5L).usuarioId(USUARIO).build();
        carrito.agregar(CarritoItem.builder().id(11L).productoId(300L).cantidad(1).build());
        return carrito;
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
        when(mercadoPago.buscarPagoDeLaCompra(REFERENCIA)).thenReturn(Optional.of(pago));

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
        verify(mercadoPago, never()).crearPreferencia(anyString(), any(), anyString(), any());

        // Y la compra anterior queda cerrada, no cancelada.
        verify(catalogo).confirmarReserva(TOKEN, REFERENCIA);
    }

    @Test
    @DisplayName("si la compra anterior no se pagó, se compensa como antes")
    void reintentoCompensaLoNoPagado() {
        SagaCheckout previa = previaViva();
        when(sagas.buscarActivasDeUsuario(USUARIO)).thenReturn(List.of(previa));
        when(mercadoPago.buscarPagoDeLaCompra(REFERENCIA)).thenReturn(Optional.empty());

        // El carrito vacío corta el checkout justo después de compensar: lo que
        // se prueba aquí es la decisión sobre la compra anterior, no el resto.
        when(carritos.buscarConItems(USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orquestador.iniciar(USUARIO, peticion(), TOKEN))
                .isInstanceOf(ConflictoException.class);

        verify(catalogo).liberarReserva(TOKEN, REFERENCIA);
    }

    @Test
    @DisplayName("conciliar y compensar pasan por el proxy, o se desharían con el error")
    void trabajoQueDebeSobrevivirPasaPorElProxy() {
        SagaCheckout previa = previaViva();
        when(sagas.buscarActivasDeUsuario(USUARIO)).thenReturn(List.of(previa));
        when(mercadoPago.buscarPagoDeLaCompra(REFERENCIA)).thenReturn(Optional.empty());
        when(carritos.buscarConItems(USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orquestador.iniciar(USUARIO, peticion(), TOKEN))
                .isInstanceOf(ConflictoException.class);

        // Las dos escriben cosas que TIENEN que sobrevivir a la excepción que
        // `iniciar` lanza justo después. Llamarlas directamente se salta el
        // proxy y con él su @Transactional(REQUIRES_NEW), así que su trabajo se
        // deshacía junto con la transacción que estaba fallando: la
        // compensación dejaba la saga diciendo ESPERANDO_PAGO, y la
        // conciliación dejaba al comprador sin la compra que sí había pagado.
        verify(proxia, times(2)).getObject();
    }

    @Test
    @DisplayName("contra entrega cierra la compra sin pasar por la pasarela")
    void contraEntregaNoVaALaPasarela() {
        MetodoPago contraEntrega = MetodoPago.builder()
                .id(2L).name("Contra entrega").description("Pago en efectivo al recibir").build();
        when(metodosPago.findById(2L)).thenReturn(Optional.of(contraEntrega));
        when(sagas.buscarActivasDeUsuario(USUARIO)).thenReturn(List.of());

        Carrito carrito = carritoConUnItem();
        when(carritos.buscarConItems(USUARIO)).thenReturn(Optional.of(carrito));
        when(carritoService.totalACobrar(carrito)).thenReturn(new BigDecimal("115.00"));
        when(sagas.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        Pedido pedido = pedidoPagable();
        when(pedidoService.crearDesdeCarrito(any(), any(), anyString(), any())).thenReturn(pedido);
        when(pedidos.buscarConDetalles(90L)).thenReturn(Optional.of(pedido));

        var respuesta = orquestador.iniciar(USUARIO, new PreferenciaRequest(2L, entrega()), TOKEN);

        // Lo que estaba mal: «Contra entrega» llevaba a MercadoPago igual que
        // todo lo demás —y sin token configurado, a un 503—, así que quien
        // elegía pagar en efectivo no podía comprar.
        verify(mercadoPago, never()).crearPreferencia(anyString(), any(), anyString(), any());

        assertThat(respuesta.requierePasarela()).isFalse();
        assertThat(respuesta.init_point()).isNull();
        assertThat(respuesta.pedidoId()).isEqualTo(90L);
        assertThat(respuesta.total()).isEqualByComparingTo("115.00");

        // El stock sale del inventario ya: dejarlo reservado lo haría caducar en
        // catálogo y el repartidor saldría con un pedido sin existencias.
        verify(catalogo).confirmarReserva(eq(TOKEN), anyString());

        // Y el pedido queda en firme SIN cobrar: el dinero llega al entregarlo.
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CONFIRMADO);
        verify(envioService).crearParaPedido(eq(pedido), any());
    }

    @Test
    @DisplayName("un pago todavía en curso NO cancela la compra")
    void pagoEnCursoNoCompensa() {
        SagaCheckout saga = previaViva();
        String paymentId = "137477397026";

        when(sagas.findByPaymentId(paymentId)).thenReturn(Optional.empty());
        // `pending` es como nace un pago en efectivo, y `in_process` una tarjeta
        // en revisión: los dos pueden acabar aprobados.
        Pago pago = new Pago(paymentId, "pending", new BigDecimal("100.00"), REFERENCIA);
        when(mercadoPago.consultarPago(paymentId)).thenReturn(pago);
        when(sagas.findByReferencia(REFERENCIA)).thenReturn(Optional.of(saga));

        assertThatThrownBy(() -> orquestador.confirmar(USUARIO, paymentId, TOKEN))
                .isInstanceOf(PagoEnCursoException.class)
                .hasMessageContaining("en proceso");

        // Lo que no puede pasar: cancelar el pedido y devolver el stock de un
        // cobro que todavía puede entrar. Con la saga en un estado final, el
        // aviso de aprobación no encontraría después nada que cerrar.
        verify(catalogo, never()).liberarReserva(anyString(), anyString());
        verify(catalogo, never()).confirmarReserva(anyString(), anyString());
        verify(metricas, never()).sagaFinalizada(anyString());
    }
}
