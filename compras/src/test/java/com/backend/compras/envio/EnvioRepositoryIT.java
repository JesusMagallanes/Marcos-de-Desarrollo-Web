package com.backend.compras.envio;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import com.backend.compras.PruebaIntegracion;
import com.backend.compras.metodopago.MetodoPago;
import com.backend.compras.metodopago.MetodoPagoRepository;
import com.backend.compras.pedido.EstadoPedido;
import com.backend.compras.pedido.Pedido;
import com.backend.compras.pedido.PedidoRepository;

/**
 * Integración contra PostgreSQL real: verifica que la máquina de estados
 * de Envio funciona con la base de datos y que las migraciones de Flyway
 * aplican correctamente.
 */
@EnabledIf(
        value = "com.backend.compras.Docker#disponible",
        disabledReason = "Docker no está disponible: se omiten las pruebas de integración")
class EnvioRepositoryIT extends PruebaIntegracion {

    @Autowired
    private EnvioRepository envioRepositorio;

    @Autowired
    private PedidoRepository pedidoRepositorio;

    @Autowired
    private MetodoPagoRepository metodoPagoRepositorio;

    /**
     * Todo pedido necesita un método de pago: la columna es NOT NULL y hay clave
     * foránea. Se crea uno propio en vez de tirar del que siembra la V1 porque
     * las pruebas comparten base de datos y otra clase podría haberlo borrado.
     */
    private MetodoPago metodoPago;

    @BeforeEach
    void limpiar() {
        envioRepositorio.deleteAll();
        pedidoRepositorio.deleteAll();

        metodoPago = metodoPagoRepositorio.findAll().stream().findFirst()
                .orElseGet(() -> metodoPagoRepositorio.save(MetodoPago.builder()
                        .name("Prueba envíos")
                        .description("Método de prueba")
                        .tipo(MetodoPago.TipoPasarela.EFECTIVO)
                        .build()));
    }

    private Pedido crearPedido() {
        return pedidoRepositorio.save(Pedido.builder()
                .usuarioId(1L)
                .metodoPago(metodoPago)
                .estado(EstadoPedido.PAGADO)
                .subtotal(new BigDecimal("100.00"))
                .costoEnvio(new BigDecimal("15.00"))
                .total(new BigDecimal("115.00"))
                .build());
    }

    @Test
    @DisplayName("un envío se crea PENDIENTe y se puede avanzar a EN_TRANSITO")
    void cicloVidaBasico() {
        Pedido pedido = crearPedido();
        Envio envio = envioRepositorio.save(Envio.builder()
                .pedido(pedido)
                .direccion("Av Lima 123")
                .estadoEnvio(EstadoEnvio.PENDIENTE)
                .build());

        assertThat(envio.getId()).isNotNull();
        assertThat(envio.getEstadoEnvio()).isEqualTo(EstadoEnvio.PENDIENTE);

        envio.marcarEnTransito();
        envioRepositorio.save(envio);

        Envio recuperado = envioRepositorio.findById(envio.getId()).orElseThrow();
        assertThat(recuperado.getEstadoEnvio()).isEqualTo(EstadoEnvio.EN_TRANSITO);
        assertThat(recuperado.getFechaEnvioProgramado()).isNotNull();
    }

    @Test
    @DisplayName("un envío EN_TRANSITO se puede marcar como ENTREGADO")
    void marcarEntregado() {
        Pedido pedido = crearPedido();
        Envio envio = envioRepositorio.save(Envio.builder()
                .pedido(pedido)
                .direccion("Av Lima 123")
                .estadoEnvio(EstadoEnvio.PENDIENTE)
                .build());

        envio.marcarEnTransito();
        envio.marcarEntregado();
        envioRepositorio.save(envio);

        Envio recuperado = envioRepositorio.findById(envio.getId()).orElseThrow();
        assertThat(recuperado.getEstadoEnvio()).isEqualTo(EstadoEnvio.ENTREGADO);
        assertThat(recuperado.getFechaEnvioEntregado()).isNotNull();
    }

    @Test
    @DisplayName("findByPedidoId encuentra el envío de un pedido")
    void buscarPorPedido() {
        Pedido pedido = crearPedido();
        envioRepositorio.save(Envio.builder()
                .pedido(pedido)
                .direccion("Av Lima 123")
                .estadoEnvio(EstadoEnvio.PENDIENTE)
                .build());

        assertThat(envioRepositorio.findByPedidoId(pedido.getId())).isPresent();
        assertThat(envioRepositorio.findByPedidoId(999L)).isEmpty();
    }

    @Test
    @DisplayName("findByEstadoEnvioOrderByIdDesc filtra por estado")
    void filtrarPorEstado() {
        Pedido p1 = crearPedido();
        Pedido p2 = Pedido.builder()
                .usuarioId(2L).metodoPago(metodoPago).estado(EstadoPedido.PAGADO)
                .subtotal(new BigDecimal("50.00")).costoEnvio(new BigDecimal("15.00"))
                .total(new BigDecimal("65.00")).build();
        p2 = pedidoRepositorio.save(p2);

        envioRepositorio.save(Envio.builder()
                .pedido(p1).direccion("Dir 1").estadoEnvio(EstadoEnvio.PENDIENTE).build());
        envioRepositorio.save(Envio.builder()
                .pedido(p2).direccion("Dir 2").estadoEnvio(EstadoEnvio.EN_TRANSITO).build());

        assertThat(envioRepositorio.findByEstadoEnvioOrderByIdDesc(EstadoEnvio.PENDIENTE)).hasSize(1);
        assertThat(envioRepositorio.findByEstadoEnvioOrderByIdDesc(EstadoEnvio.EN_TRANSITO)).hasSize(1);
        assertThat(envioRepositorio.findByEstadoEnvioOrderByIdDesc(EstadoEnvio.ENTREGADO)).isEmpty();
    }
}
