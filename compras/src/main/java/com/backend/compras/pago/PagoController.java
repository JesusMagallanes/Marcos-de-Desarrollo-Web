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
import com.backend.compras.pago.dto.PagoDtos.VerificarResponse;
import com.backend.compras.pedido.dto.PedidoDtos.PedidoResponse;
import com.backend.compras.saga.CheckoutOrquestador;
import com.backend.compras.shared.error.DemasiadasPeticionesException;
import com.backend.compras.shared.metricas.MetricasSeguridad;
import com.backend.compras.shared.security.TokenActual;
import com.backend.compras.shared.security.UsuarioAutenticado;
import com.backend.compras.shared.seguridad.LimitadorPeticiones;

import java.time.Duration;

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
    private final LimitadorPeticiones limitador;

    /*
     * Cupo de checkouts POR COMPRADOR.
     *
     * El filtro de rate limit cuenta por IP, y detrás del gateway todos los
     * compradores comparten la misma: el cupo de pagos era, en la práctica, un
     * tope para la tienda entera. Aquí la identidad ya está verificada por el
     * JWT, así que el límite recae sobre quien se quiere limitar de verdad.
     *
     * Diez es holgado para alguien que compra —y que puede tener que reintentar
     * porque se le agotó el stock o cambió de idea— y sigue frenando a quien
     * intente abrir checkouts en bucle, que cada uno reserva stock y llama a la
     * pasarela.
     */
    private static final int MAX_CHECKOUTS_POR_USUARIO = 10;
    private static final Duration VENTANA_CHECKOUT = Duration.ofMinutes(10);

    /** Fase 1: reserva stock, crea el pedido pendiente y devuelve la URL de pago. */
    @PostMapping("/preferencia")
    public PreferenciaResponse crearPreferencia(UsuarioAutenticado usuario,
            @Valid @RequestBody PreferenciaRequest peticion) {
        exigirCupoDeCheckout(usuario.id());
        return orquestador.iniciar(usuario.id(), peticion, TokenActual.valor());
    }

    /** Fase 2: verifica el pago contra la pasarela y cierra la saga. */
    @PostMapping("/confirmar")
    public PedidoResponse confirmar(UsuarioAutenticado usuario,
            @Valid @RequestBody ConfirmarRequest peticion) {
        return orquestador.confirmar(usuario.id(), peticion.paymentId(), TokenActual.valor());
    }

    /**
     * Fase 2 alternativa: el comprador volvió de la pasarela sin
     * {@code payment_id} —vuelta a pulso, o back_urls que MercadoPago no pudo
     * registrar— y se le pregunta a la pasarela si ya cobró.
     *
     * <p>Idempotente y seguro de repetir: solo mira, nunca compensa, y si el
     * pago entró cierra la compra por el mismo camino que {@code /confirmar}.
     * Sin límite de peticiones a propósito, por la misma razón que su hermano:
     * quien ya pagó no debe chocar con un 429 al volver a la tienda.
     */
    @PostMapping("/verificar")
    public VerificarResponse verificar(UsuarioAutenticado usuario) {
        return orquestador.verificar(usuario.id(), TokenActual.valor());
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
     * Solo lo aplica {@code /preferencia}: confirmar es idempotente y se
     * reintenta por motivos legítimos —recargar la página de retorno— así que
     * limitarlo solo estorbaría a quien ya pagó.
     */
    private void exigirCupoDeCheckout(Long usuarioId) {
        String clave = "checkout|usuario:" + usuarioId;
        if (limitador.permitir(clave, MAX_CHECKOUTS_POR_USUARIO, VENTANA_CHECKOUT)) {
            return;
        }

        long espera = limitador.segundosParaReintentar(clave, VENTANA_CHECKOUT);
        metricas.rateLimitBloqueado("checkout-usuario");
        log.warn("El usuario {} agotó su cupo de checkouts", usuarioId);

        throw new DemasiadasPeticionesException(
                "Has empezado demasiadas compras seguidas. Espera " + espera + " segundos.",
                espera);
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
