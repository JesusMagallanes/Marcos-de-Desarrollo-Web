package com.backend.compras.pago;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/** A08 (Fallos de integridad). */
@Component
@Slf4j
public class WebhookMercadoPago {

    @Value("${mercadopago.webhook-secret:}")
    private String secreto;

    public boolean firmaValida(String cabeceraFirma, String requestId, String dataId) {
        if (secreto == null || secreto.isBlank()) {
            // Sin secreto configurado no se puede verificar nada: se rechaza
            // en lugar de aceptar a ciegas.
            log.warn("MP_WEBHOOK_SECRET no configurado: se rechazan los webhooks");
            return false;
        }
        if (cabeceraFirma == null || dataId == null) {
            return false;
        }

        String ts = null;
        String v1 = null;
        for (String parte : cabeceraFirma.split(",")) {
            String[] par = parte.trim().split("=", 2);
            if (par.length != 2) {
                continue;
            }
            if ("ts".equals(par[0])) {
                ts = par[1];
            } else if ("v1".equals(par[0])) {
                v1 = par[1];
            }
        }

        if (ts == null || v1 == null) {
            return false;
        }

        String mensaje = "id:%s;request-id:%s;ts:%s;".formatted(
                dataId, requestId == null ? "" : requestId, ts);

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String calculada = HexFormat.of().formatHex(
                    mac.doFinal(mensaje.getBytes(StandardCharsets.UTF_8)));

            // Comparación en tiempo constante: un equals normal filtra
            // información por el tiempo de respuesta.
            return MessageDigest.isEqual(
                    calculada.getBytes(StandardCharsets.UTF_8),
                    v1.getBytes(StandardCharsets.UTF_8));

        } catch (Exception ex) {
            log.error("Error verificando la firma del webhook: {}", ex.getMessage());
            return false;
        }
    }

    /** De momento solo se deja constancia. */
    public void procesar(String dataId, String cuerpo) {
        log.info("Webhook verificado de MercadoPago: pago={}", dataId);
    }
}
