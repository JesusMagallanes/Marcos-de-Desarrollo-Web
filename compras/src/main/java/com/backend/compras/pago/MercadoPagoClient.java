package com.backend.compras.pago;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.backend.compras.shared.error.ServicioNoDisponibleException;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class MercadoPagoClient {

    private final RestClient cliente;
    private final String accessToken;
    private final String retornoBase;

    public MercadoPagoClient(RestClient mercadoPagoRestClient,
            @Value("${mercadopago.access-token}") String accessToken,
            @Value("${mercadopago.retorno-base}") String retornoBase) {
        this.cliente = mercadoPagoRestClient;
        this.accessToken = accessToken;
        this.retornoBase = retornoBase;
    }

    public record Preferencia(String id, String init_point, String sandbox_init_point) {
    }

    /** Estado de un pago consultado a la pasarela. */
    public record Pago(String id, String status, BigDecimal transaction_amount, String external_reference) {

        public boolean aprobado() {
            return "approved".equals(status);
        }
    }

    public boolean configurado() {
        return accessToken != null && !accessToken.isBlank();
    }

    /**
     * El importe llega ya calculado por el servidor. La URL de retorno se construye desde
     * configuración, no está fija a localhost:8080 como en el monolito.
     */
    public Preferencia crearPreferencia(String titulo, BigDecimal importe, String referenciaExterna) {
        exigirConfiguracion();

        Map<String, Object> item = Map.of(
                "title", titulo,
                "quantity", 1,
                "unit_price", importe,
                "currency_id", "PEN");

        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("items", List.of(item));
        cuerpo.put("external_reference", referenciaExterna);
        cuerpo.put("back_urls", Map.of(
                "success", retornoBase + "/carrito?status=approved",
                "failure", retornoBase + "/carrito?status=failure",
                "pending", retornoBase + "/carrito?status=pending"));

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
     * Busca si existe un pago aprobado para una referencia de compra.
     *
     * <p>Hace falta para conciliar: cuando alguien paga y cierra la pestaña sin
     * volver, no tenemos su {@code paymentId} —ese llega por la URL de retorno—,
     * solo la referencia que pusimos en la preferencia. Sin esta consulta, el
     * barrendero daba la compra por abandonada y la compensaba: pedido cancelado,
     * stock devuelto y el cobro hecho.
     *
     * @return el pago aprobado, o vacío si no hay ninguno
     */
    public Optional<Pago> buscarPagoAprobado(String referencia) {
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
            return respuesta.results().stream().filter(Pago::aprobado).findFirst();

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
