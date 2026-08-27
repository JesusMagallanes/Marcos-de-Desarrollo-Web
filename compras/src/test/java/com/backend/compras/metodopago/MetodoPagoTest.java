package com.backend.compras.metodopago;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.backend.compras.metodopago.dto.MetodoPagoDtos.MetodoPagoRequest;
import com.backend.compras.metodopago.dto.MetodoPagoDtos.MetodoPagoResponse;

@DisplayName("MetodoPago - esMercadoPago")
class MetodoPagoTest {

    @Test
    @DisplayName("un método con tipo MERCADOPAGO devuelve true")
    void mercadopagoPorTipo() {
        MetodoPago mp = MetodoPago.builder()
                .name("MercadoPago Checkout")
                .description("Pago online")
                .tipo(MetodoPago.TipoPasarela.MERCADOPAGO)
                .build();

        assertThat(mp.esMercadoPago()).isTrue();
    }

    @Test
    @DisplayName("un método con tipo OTRO devuelve false aunque el nombre contenga mercadopago")
    void otroTipoConNombreMercadopago() {
        MetodoPago mp = MetodoPago.builder()
                .name("MercadoPago viejo")
                .description("Descontinuado")
                .tipo(MetodoPago.TipoPasarela.OTRO)
                .build();

        assertThat(mp.esMercadoPago()).isFalse();
    }

    @Test
    @DisplayName("un método con tipo EFECTIVO devuelve false")
    void efectivo() {
        MetodoPago mp = MetodoPago.builder()
                .name("Contra entrega")
                .description("Pago en efectivo")
                .tipo(MetodoPago.TipoPasarela.EFECTIVO)
                .build();

        assertThat(mp.esMercadoPago()).isFalse();
    }

    @Test
    @DisplayName("el tipo por defecto es OTRO")
    void tipoPorDefecto() {
        MetodoPago mp = MetodoPago.builder()
                .name("Test")
                .description("Test")
                .build();

        assertThat(mp.getTipo()).isEqualTo(MetodoPago.TipoPasarela.OTRO);
        assertThat(mp.esMercadoPago()).isFalse();
    }

    /*
     * El tipo TIENE que poder entrar y salir por la API.
     *
     * Cuando `esMercadoPago()` dejó de mirar el nombre, el formulario del panel
     * seguía mandando solo nombre y descripción: todo método creado desde ahí
     * nacía OTRO y el checkout lo cerraba como contra entrega. Un método
     * llamado «MercadoPago» daba el pedido por bueno, confirmaba el stock y
     * vaciaba el carrito SIN COBRAR NADA.
     */

    @Test
    @DisplayName("la respuesta expone el tipo: el panel tiene que poder verlo")
    void laRespuestaLlevaElTipo() {
        MetodoPago mp = MetodoPago.builder()
                .name("MercadoPago").description("Pago online")
                .tipo(MetodoPago.TipoPasarela.MERCADOPAGO)
                .build();

        assertThat(MetodoPagoResponse.desde(mp).tipo())
                .isEqualTo(MetodoPago.TipoPasarela.MERCADOPAGO);
    }

    @Test
    @DisplayName("la petición acepta el tipo, y el saneado del texto no se lo lleva")
    void laPeticionLlevaElTipo() {
        MetodoPagoRequest dto = new MetodoPagoRequest(
                "  MercadoPago  ", "  Pago online  ", MetodoPago.TipoPasarela.MERCADOPAGO);

        assertThat(dto.tipo()).isEqualTo(MetodoPago.TipoPasarela.MERCADOPAGO);
        assertThat(dto.name()).isEqualTo("MercadoPago");
    }

    @Test
    @DisplayName("un método creado desde la petición conserva el tipo elegido")
    void seConstruyeConElTipoDeLaPeticion() {
        MetodoPagoRequest dto = new MetodoPagoRequest(
                "Contra entrega", "Efectivo al recibir", MetodoPago.TipoPasarela.EFECTIVO);

        MetodoPago mp = MetodoPago.builder()
                .name(dto.name()).description(dto.description()).tipo(dto.tipo())
                .build();

        assertThat(mp.getTipo()).isEqualTo(MetodoPago.TipoPasarela.EFECTIVO);
        assertThat(mp.esMercadoPago()).isFalse();
    }
}
