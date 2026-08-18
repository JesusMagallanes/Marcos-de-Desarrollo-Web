package com.backend.compras.pago;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.backend.compras.pago.dto.PagoDtos.ConfirmarRequest;
import com.backend.compras.pago.dto.PagoDtos.PreferenciaRequest;
import com.backend.compras.pago.dto.PagoDtos.PreferenciaResponse;
import com.backend.compras.pedido.dto.PedidoDtos.PedidoResponse;
import com.backend.compras.saga.CheckoutOrquestador;
import com.backend.compras.shared.metricas.MetricasSeguridad;
import com.backend.compras.shared.security.TokenActual;
import com.backend.compras.shared.security.UsuarioAutenticado;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fachada del checkout. Toda la coordinación vive en el orquestador de la saga.
 */
@RestController
@RequestMapping("/api/pagos")
@RequiredArgsConstructor
@Slf4j
public class PagoController {

    private final CheckoutOrquestador orquestador;
    private final WebhookMercadoPago webhook;
    private final MetricasSeguridad metricas;

    /** Fase 1: reserva stock, crea el pedido pendiente y devuelve la URL de pago. */
    @PostMapping("/preferencia")
    public PreferenciaResponse crearPreferencia(UsuarioAutenticado usuario,
            @Valid @RequestBody PreferenciaRequest peticion) {
        return orquestador.iniciar(usuario.id(), peticion, TokenActual.valor());
    }

    /** Fase 2: verifica el pago contra la pasarela y cierra la saga. */
    @PostMapping("/confirmar")
    public PedidoResponse confirmar(UsuarioAutenticado usuario,
            @Valid @RequestBody ConfirmarRequest peticion) {
        return orquestador.confirmar(usuario.id(), peticion.paymentId(), TokenActual.valor());
    }

    /** Notificación servidor-a-servidor de MercadoPago. */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestHeader(value = "x-signature", required = false) String firma,
            @RequestHeader(value = "x-request-id", required = false) String requestId,
            @RequestParam(value = "data.id", required = false) String dataId,
            @RequestParam(value = "type", required = false) String tipo,
            @RequestParam(value = "topic", required = false) String topico,
            @RequestBody(required = false) String cuerpo) {

        /*
         * MercadoPago avisa de varias cosas por la misma URL, no solo de pagos:
         * también manda `merchant_order`, y esas llegan con otro formato y sin
         * `data.id`. Sin distinguirlas caían en la rama de abajo y se registraban
         * como "firma inválida", que es una pista falsa: quien mire el log irá a
         * revisar el secreto en vez del pago que sí falta.
         */
        if (!esDePago(tipo, topico)) {
            log.debug("Aviso de MercadoPago ignorado (type={}, topic={})", tipo, topico);
            return ResponseEntity.ok().build();
        }

        if (!webhook.firmaValida(firma, requestId, dataId)) {
            metricas.webhookRechazado();
            log.warn("Webhook con firma inválida rechazado (requestId={})", requestId);
            // 200 a propósito: no se le da información al que sondea.
            return ResponseEntity.ok().build();
        }

        webhook.procesar(dataId, cuerpo);
        return ResponseEntity.ok().build();
    }

    /**
     * Los avisos de pago vienen como {@code type=payment} en los webhooks
     * actuales y como {@code topic=payment} en los antiguos. Cuando no viene
     * ninguno de los dos se sigue adelante: es preferible intentar procesar un
     * aviso raro —la firma decide— que descartar un cobro por un parámetro.
     */
    private boolean esDePago(String tipo, String topico) {
        String clase = tipo != null ? tipo : topico;
        return clase == null || clase.isBlank() || clase.contains("payment");
    }
}
