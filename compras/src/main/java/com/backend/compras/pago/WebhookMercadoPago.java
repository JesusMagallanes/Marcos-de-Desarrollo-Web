package com.backend.compras.pago;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.backend.compras.pago.MercadoPagoClient.Pago;
import com.backend.compras.saga.CheckoutOrquestador;
import com.backend.compras.saga.SagaCheckoutRepository;
import com.backend.compras.shared.seguridad.ContextoRls;
import com.backend.compras.shared.seguridad.TokenServicio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** A08 (Fallos de integridad). */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookMercadoPago {

    private final MercadoPagoClient mercadoPago;
    private final SagaCheckoutRepository sagas;
    private final CheckoutOrquestador orquestador;
    private final TokenServicio tokenServicio;

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

    /**
     * Cierra la compra que corresponde a este pago.
     *
     * <p>Antes esto solo escribía una línea en el log, y ahí estaba el agujero:
     * el aviso de MercadoPago llega aunque el comprador cierre la pestaña sin
     * volver, y era la única señal de que el cobro se había hecho. Sin
     * atenderla, el barrendero daba la compra por abandonada.
     *
     * <p>El barrendero sigue existiendo y sigue conciliando: este camino es el
     * rápido —cierra en segundos en vez de en la siguiente pasada— pero no el
     * único, porque un webhook se puede perder y nunca hay que depender de que
     * llegue.
     *
     * <p>Es idempotente por partida doble: MercadoPago reintenta sus avisos, y
     * el usuario puede volver a la tienda a la vez que llega este. Lo garantiza
     * `confirmar`, que devuelve el pedido tal cual si la saga ya está completa.
     *
     * <p>No lanza nada: al webhook siempre se le responde 200. Si esto fallara,
     * MercadoPago reintentaría y, si tampoco, quedaría el barrendero.
     */
    public void procesar(String dataId, String cuerpo) {
        log.info("Webhook verificado de MercadoPago: pago={}", dataId);

        if (dataId == null || dataId.isBlank()) {
            return;
        }

        try {
            // Sin usuario ni petición HTTP detrás: hace falta contexto de sistema
            // para que RLS deje ver la saga, y un token propio para hablar con
            // catálogo.
            ContextoRls.comoSistema(() -> conciliar(dataId));
        } catch (RuntimeException ex) {
            log.error("No se pudo conciliar el pago {} desde el webhook: {}", dataId, ex.getMessage());
        }
    }

    private void conciliar(String paymentId) {
        Pago pago = mercadoPago.consultarPago(paymentId);

        if (!pago.aprobado()) {
            log.info("Webhook de {} en estado {}: no hay nada que cerrar", paymentId, pago.status());
            return;
        }

        sagas.findByReferencia(pago.external_reference())
                .ifPresentOrElse(
                        saga -> orquestador.confirmar(saga.getUsuarioId(), paymentId, tokenServicio.emitir()),
                        () -> log.warn("Webhook de {} sin compra asociada (referencia={})",
                                paymentId, pago.external_reference()));
    }
}
