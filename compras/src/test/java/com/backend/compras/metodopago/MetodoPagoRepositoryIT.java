package com.backend.compras.metodopago;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import com.backend.compras.PruebaIntegracion;
import com.backend.compras.envio.EnvioRepository;
import com.backend.compras.pedido.PedidoRepository;

/**
 * Integración contra PostgreSQL real: verifica que la columna `tipo` de
 * MetodoPago se mapea correctamente con la migración V11.
 */
@EnabledIf(
        value = "com.backend.compras.Docker#disponible",
        disabledReason = "Docker no está disponible: se omiten las pruebas de integración")
class MetodoPagoRepositoryIT extends PruebaIntegracion {

    @Autowired
    private MetodoPagoRepository repositorio;

    @Autowired
    private EnvioRepository envios;

    @Autowired
    private PedidoRepository pedidos;

    /**
     * Antes de vaciar los métodos de pago hay que vaciar lo que los referencia.
     *
     * <p>La base es la misma para todas las clases de integración, así que aquí
     * pueden quedar pedidos creados por otra —{@code EnvioRepositoryIT} deja
     * varios—, y {@code pedido.metodopago_id} tiene clave foránea. Sin esto, el
     * borrado chocaba con {@code fk_pedido_metodopago}.
     */
    @BeforeEach
    void limpiar() {
        envios.deleteAll();
        pedidos.deleteAll();
        repositorio.deleteAll();
    }

    @Test
    @DisplayName("un método con tipo MERCADOPAGO se persiste y recupera correctamente")
    void persisteMercadoPago() {
        MetodoPago mp = repositorio.save(MetodoPago.builder()
                .name("MercadoPago Checkout")
                .description("Pago online con MercadoPago")
                .tipo(MetodoPago.TipoPasarela.MERCADOPAGO)
                .build());

        assertThat(mp.getId()).isNotNull();

        MetodoPago recuperado = repositorio.findById(mp.getId()).orElseThrow();
        assertThat(recuperado.getTipo()).isEqualTo(MetodoPago.TipoPasarela.MERCADOPAGO);
        assertThat(recuperado.esMercadoPago()).isTrue();
    }

    @Test
    @DisplayName("un método con tipo EFECTIVO se persiste correctamente")
    void persisteEfectivo() {
        MetodoPago mp = repositorio.save(MetodoPago.builder()
                .name("Contra entrega")
                .description("Pago en efectivo al recibir")
                .tipo(MetodoPago.TipoPasarela.EFECTIVO)
                .build());

        MetodoPago recuperado = repositorio.findById(mp.getId()).orElseThrow();
        assertThat(recuperado.getTipo()).isEqualTo(MetodoPago.TipoPasarela.EFECTIVO);
        assertThat(recuperado.esMercadoPago()).isFalse();
    }

    @Test
    @DisplayName("el tipo por defecto es OTRO cuando no se especifica")
    void tipoPorDefecto() {
        MetodoPago mp = repositorio.save(MetodoPago.builder()
                .name("Test")
                .description("Test")
                .build());

        MetodoPago recuperado = repositorio.findById(mp.getId()).orElseThrow();
        assertThat(recuperado.getTipo()).isEqualTo(MetodoPago.TipoPasarela.OTRO);
    }

    @Test
    @DisplayName("el nombre es único")
    void nombreUnico() {
        repositorio.save(MetodoPago.builder()
                .name("MercadoPago").description("MP").tipo(MetodoPago.TipoPasarela.MERCADOPAGO).build());

        assertThat(repositorio.findAll()).hasSize(1);
    }
}
