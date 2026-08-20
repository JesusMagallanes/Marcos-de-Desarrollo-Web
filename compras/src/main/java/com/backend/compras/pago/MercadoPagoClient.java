package com.backend.compras.pago;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Optional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.backend.compras.pago.dto.DireccionEntrega;
import com.backend.compras.shared.error.ServicioNoDisponibleException;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class MercadoPagoClient {

    private final RestClient cliente;
    private final String accessToken;
    private final String retornoBase;
    private final String notificacionUrl;

    public MercadoPagoClient(RestClient mercadoPagoRestClient,
            @Value("${mercadopago.access-token}") String accessToken,
            @Value("${mercadopago.retorno-base}") String retornoBase,
            @Value("${mercadopago.notificacion-url:}") String notificacionUrl) {
        this.cliente = mercadoPagoRestClient;
        this.accessToken = accessToken;
        this.retornoBase = retornoBase;
        this.notificacionUrl = notificacionUrl;
    }

    /**
     * Avisa al arrancar de las dos formas en que este checkout se rompe en
     * silencio.
     *
     * <p>Las dos han pasado de verdad y ninguna da error: MercadoPago acepta la
     * preferencia con un 201 y guarda las {@code back_urls} VACIAS si apuntan a
     * localhost, y sin {@code notification_url} no llama al webhook nunca. El
     * resultado es el mismo por los dos lados —el comprador paga, el dinero
     * entra en MercadoPago y la tienda no se entera— y solo se descubre mirando
     * la preferencia en la API, que es donde nadie mira.
     */
    @PostConstruct
    void avisarSiElCobroNoPuedeVolver() {
        if (!configurado()) {
            log.warn("MercadoPago sin MP_ACCESS_TOKEN: el checkout respondera 503.");
            return;
        }

        if (!esAlcanzablePorMercadoPago(retornoBase)) {
            log.warn("MP_RETORNO_BASE={} no es publica: MercadoPago DESCARTA las back_urls"
                    + " (las guarda vacias, sin dar error) y el comprador se queda sin boton"
                    + " para volver a la tienda, asi que /api/pagos/confirmar no se ejecuta."
                    + " Usa una URL publica: ngrok en desarrollo, el dominio al desplegar.",
                    retornoBase);
        }

        if (!esAlcanzablePorMercadoPago(notificacionUrl)) {
            log.warn("MP_NOTIFICACION_URL sin URL publica: no se manda notification_url, asi"
                    + " que MercadoPago no avisara del cobro. La venta solo se cerrara cuando"
                    + " el barrendero concilie, y hasta entonces el pedido sigue PENDIENTE.");
        }
    }

    public record Preferencia(String id, String init_point, String sandbox_init_point) {
    }

    /** Estado de un pago consultado a la pasarela. */
    public record Pago(String id, String status, BigDecimal transaction_amount, String external_reference) {

        /*
         * Estados en los que la pasarela AUN NO HA DICHO la ultima palabra.
         *
         * Distinguirlos de un rechazo no es un matiz: un pago en efectivo nace
         * `pending` y se aprueba cuando el comprador va a pagarlo, y una tarjeta
         * en revision pasa por `in_process`. Tratandolos como fallo se cancelaba
         * el pedido y se devolvia el stock de un cobro que despues entraba, y la
         * saga quedaba COMPENSADA --estado final--, asi que cuando llegaba el
         * aviso de aprobacion ya no habia nada que cerrar.
         */
        private static final Set<String> EN_CURSO = Set.of("pending", "in_process", "authorized");

        public boolean aprobado() {
            return "approved".equals(status);
        }

        /** Ni aprobado ni rechazado todavia: hay que esperar, no compensar. */
        public boolean enCurso() {
            return status != null && EN_CURSO.contains(status);
        }
    }

    public boolean configurado() {
        return accessToken != null && !accessToken.isBlank();
    }

    /**
     * El importe llega ya calculado por el servidor. La URL de retorno se construye desde
     * configuración, no está fija a localhost:8080 como en el monolito.
     */
    public Preferencia crearPreferencia(String titulo, BigDecimal importe, String referenciaExterna,
            DireccionEntrega entrega) {
        exigirConfiguracion();

        Map<String, Object> item = Map.of(
                "title", titulo,
                "quantity", 1,
                "unit_price", importe,
                "currency_id", "PEN");

        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("items", List.of(item));
        cuerpo.put("external_reference", referenciaExterna);

        /*
         * El destino, en los campos que entiende la pasarela.
         *
         * Con esto MercadoPago enseña la direccion en su propia pantalla y puede
         * calcular el envio a partir del codigo postal. Sin ello el comprador ve
         * un importe y un nombre de producto, y tiene que fiarse de que va al
         * sitio correcto. La traduccion de nuestros nombres a los suyos vive
         * entera en DireccionEntrega.
         */
        if (entrega != null) {
            cuerpo.put("shipments", Map.of("receiver_address", entrega.comoReceiverAddress()));
        }
        cuerpo.put("back_urls", Map.of(
                "success", retornoBase + "/carrito?status=approved",
                "failure", retornoBase + "/carrito?status=failure",
                "pending", retornoBase + "/carrito?status=pending"));

        /*
         * A donde avisa MercadoPago en cuanto se cobra. Es lo que cierra la
         * venta sin depender de que el comprador vuelva a la tienda, y mucha
         * gente cierra la pestania nada mas ver "pago aprobado".
         *
         * Faltaba por completo, y por eso el webhook --que esta escrito, con su
         * firma verificada y publicado en /api/pagos/webhook-- no se llamaba
         * nunca: MercadoPago no adivina la direccion, hay que darsela aqui, en
         * cada preferencia.
         */
        if (esAlcanzablePorMercadoPago(notificacionUrl)) {
            cuerpo.put("notification_url", notificacionUrl);
        }

        // `auto_return` hace que MercadoPago devuelva al comprador solo cuando
        // el pago se aprueba, pero exige que back_urls.success sea una URL que
        // MercadoPago pueda alcanzar. Con localhost responde
        // "auto_return invalid. back_url.success must be defined", así que en
        // desarrollo se omite: el usuario vuelve pulsando el enlace de la
        // pasarela y el flujo sigue funcionando igual.
        if (esAlcanzablePorMercadoPago(retornoBase)) {
            cuerpo.put("auto_return", "approved");
        } else {
            log.debug("Retorno {} no es público: se omite auto_return", retornoBase);
        }

        try {
            Preferencia preferencia = cliente.post()
                    .uri("/checkout/preferences")
                    .header("Authorization", "Bearer " + accessToken)
                    .body(cuerpo)
                    .retrieve()
                    .body(Preferencia.class);

            if (preferencia == null) {
                throw new ServicioNoDisponibleException("MercadoPago no devolvió una preferencia válida");
            }
            return preferencia;

        } catch (RestClientException ex) {
            log.error("Fallo creando preferencia en MercadoPago: {}", ex.getMessage());
            throw new ServicioNoDisponibleException("No se pudo iniciar el pago. Intenta de nuevo.");
        }
    }

    /**
     * Verifica un pago contra la API. Es el paso que faltaba por completo en el monolito:
     * allí `confirmarPago` creaba el pedido sin comprobar nada.
     */
    public Pago consultarPago(String paymentId) {
        exigirConfiguracion();
        try {
            Pago pago = cliente.get()
                    .uri("/v1/payments/{id}", paymentId)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(Pago.class);

            if (pago == null) {
                throw new ServicioNoDisponibleException("MercadoPago no devolvió información del pago");
            }
            return pago;

        } catch (RestClientException ex) {
            log.error("Fallo consultando el pago {}: {}", paymentId, ex.getMessage());
            throw new ServicioNoDisponibleException("No se pudo verificar el pago.");
        }
    }

    /** Lo que devuelve la búsqueda de pagos: solo interesa la lista. */
    public record BusquedaPagos(List<Pago> results) {
    }

    /**
     * Busca el pago que decide la suerte de una compra.
     *
     * <p>Hace falta para conciliar: cuando alguien paga y cierra la pestaña sin
     * volver, no tenemos su {@code paymentId} —ese llega por la URL de retorno—,
     * solo la referencia que pusimos en la preferencia. Sin esta consulta, el
     * barrendero daba la compra por abandonada y la compensaba: pedido cancelado,
     * stock devuelto y el cobro hecho.
     *
     * <p>Devuelve el aprobado si lo hay y, si no, uno que siga en curso. Ese
     * segundo caso importa tanto como el primero: un pago en efectivo o una
     * tarjeta en revisión todavía pueden aprobarse, y quien vaya a tirar la
     * compra tiene que saber que no es lo mismo que un rechazo.
     *
     * @return el pago aprobado; si no lo hay, uno aún en curso; si tampoco, vacío
     */
    public Optional<Pago> buscarPagoDeLaCompra(String referencia) {
        exigirConfiguracion();
        try {
            BusquedaPagos respuesta = cliente.get()
                    .uri(uri -> uri.path("/v1/payments/search")
                            .queryParam("external_reference", referencia)
                            .build())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(BusquedaPagos.class);

            if (respuesta == null || respuesta.results() == null) {
                return Optional.empty();
            }
            // Un aprobado manda sobre cualquier otro: es el unico que cierra la
            // venta. Los demas solo sirven para saber que hay que esperar.
            return respuesta.results().stream()
                    .filter(pago -> pago.aprobado() || pago.enCurso())
                    .min(Comparator.comparingInt(pago -> pago.aprobado() ? 0 : 1));

        } catch (RestClientException ex) {
            // No se propaga: si la pasarela no responde, lo prudente es NO tocar
            // la saga y reintentar en la siguiente pasada. Compensar a ciegas es
            // lo que se está evitando.
            log.warn("No se pudo consultar pagos de {}: {}", referencia, ex.getMessage());
            return Optional.empty();
        }
    }

    /** MercadoPago no acepta localhost ni IPs privadas como URL de retorno. */
    private boolean esAlcanzablePorMercadoPago(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String u = url.toLowerCase();
        return !(u.contains("localhost")
                || u.contains("127.0.0.1")
                || u.contains("://192.168.")
                || u.contains("://10.")
                || u.contains("://0.0.0.0"));
    }

    private void exigirConfiguracion() {
        if (!configurado()) {
            throw new ServicioNoDisponibleException(
                    "MercadoPago no está configurado: define MP_ACCESS_TOKEN.");
        }
    }
}
