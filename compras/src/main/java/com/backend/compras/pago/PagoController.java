package com.backend.compras.pago;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
        return orquestador.iniciar(usuario.id(), peticion.metodoPagoId(), TokenActual.valor());
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
            @org.springframework.web.bind.annotation.RequestHeader(value = "x-signature", required = false) String firma,
            @org.springframework.web.bind.annotation.RequestHeader(value = "x-request-id", required = false) String requestId,
            @org.springframework.web.bind.annotation.RequestParam(value = "data.id", required = false) String dataId,
            @RequestBody(required = false) String cuerpo) {

        if (!webhook.firmaValida(firma, requestId, dataId)) {
            metricas.webhookRechazado();
            log.warn("Webhook con firma inválida rechazado (requestId={})", requestId);
            // 200 a propósito: no se le da información al que sondea.
            return ResponseEntity.ok().build();
        }

        webhook.procesar(dataId, cuerpo);
        return ResponseEntity.ok().build();
    }
}
