package com.backend.compras.pago;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * A08: sin verificar la firma, cualquiera que conozca la URL del webhook puede
 * enviar un "pago aprobado" falso.
 */
class WebhookMercadoPagoTest {

    private static final String SECRETO = "secreto-de-webhook-para-pruebas";
    private static final String DATA_ID = "1234567890";
    private static final String REQUEST_ID = "req-abc";
    private static final String TS = "1700000000";

    private WebhookMercadoPago webhook;

    @BeforeEach
    void preparar() {
        webhook = new WebhookMercadoPago();
        ReflectionTestUtils.setField(webhook, "secreto", SECRETO);
    }

    private String firmar(String dataId, String requestId, String ts, String secreto) throws Exception {
        String mensaje = "id:%s;request-id:%s;ts:%s;".formatted(dataId, requestId, ts);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(mensaje.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("acepta una firma legítima")
    void firmaValida() throws Exception {
        String v1 = firmar(DATA_ID, REQUEST_ID, TS, SECRETO);
        String cabecera = "ts=%s,v1=%s".formatted(TS, v1);

        assertThat(webhook.firmaValida(cabecera, REQUEST_ID, DATA_ID)).isTrue();
    }

    @Test
    @DisplayName("rechaza una firma calculada con otro secreto")
    void secretoDistinto() throws Exception {
        String v1 = firmar(DATA_ID, REQUEST_ID, TS, "secreto-del-atacante-que-no-vale");
        String cabecera = "ts=%s,v1=%s".formatted(TS, v1);

        assertThat(webhook.firmaValida(cabecera, REQUEST_ID, DATA_ID)).isFalse();
    }

    @Test
    @DisplayName("rechaza si se manipula el identificador del pago")
    void dataIdManipulado() throws Exception {
        String v1 = firmar(DATA_ID, REQUEST_ID, TS, SECRETO);
        String cabecera = "ts=%s,v1=%s".formatted(TS, v1);

        assertThat(webhook.firmaValida(cabecera, REQUEST_ID, "9999999999")).isFalse();
    }

    @Test
    @DisplayName("rechaza cabeceras ausentes o incompletas")
    void cabecerasIncompletas() {
        assertThat(webhook.firmaValida(null, REQUEST_ID, DATA_ID)).isFalse();
        assertThat(webhook.firmaValida("ts=1700000000", REQUEST_ID, DATA_ID)).isFalse();
        assertThat(webhook.firmaValida("v1=abc", REQUEST_ID, DATA_ID)).isFalse();
        assertThat(webhook.firmaValida("basura", REQUEST_ID, DATA_ID)).isFalse();
    }

    @Test
    @DisplayName("sin data.id no hay nada que verificar")
    void sinDataId() throws Exception {
        String v1 = firmar(DATA_ID, REQUEST_ID, TS, SECRETO);
        assertThat(webhook.firmaValida("ts=%s,v1=%s".formatted(TS, v1), REQUEST_ID, null)).isFalse();
    }

    @Test
    @DisplayName("sin secreto configurado se rechaza todo en vez de aceptar a ciegas")
    void sinSecretoConfigurado() throws Exception {
        ReflectionTestUtils.setField(webhook, "secreto", "");
        String v1 = firmar(DATA_ID, REQUEST_ID, TS, SECRETO);

        assertThat(webhook.firmaValida("ts=%s,v1=%s".formatted(TS, v1), REQUEST_ID, DATA_ID)).isFalse();
    }
}
