package com.backend.compras.shared.catalogo;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.MDC;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.backend.compras.shared.error.ConflictoException;
import com.backend.compras.shared.resiliencia.Cortacircuitos;
import com.backend.compras.shared.resiliencia.Reintentos;
import com.backend.compras.shared.error.ServicioNoDisponibleException;

import lombok.extern.slf4j.Slf4j;

/** Única dependencia saliente de compras, y participante remoto de la saga. */
@Component
@Slf4j
public class CatalogoClient {

    private final RestClient cliente;
    private final Cortacircuitos cortacircuitos =
            new Cortacircuitos("catalogo", 5, Duration.ofSeconds(30));

    public CatalogoClient(RestClient catalogoRestClient) {
        this.cliente = catalogoRestClient;
    }

    public record LineaPrecio(Long productoId, String nombre, String imageUrl, BigDecimal precio, Integer stock) {
    }

    public record AjusteStock(Long productoId, Integer cantidad) {
    }

    /** Precios y stock vigentes. Es la fuente de verdad para calcular totales. */
    public List<LineaPrecio> precios(String token, List<Long> productoIds) {
        if (productoIds.isEmpty()) {
            return List.of();
        }

        return Reintentos.conEsperaExponencial("catalogo.precios", 3, () ->
                cortacircuitos.ejecutar(() -> {
                    try {
                        List<LineaPrecio> respuesta = cliente.post()
                                .uri("/api/inventario/precios")
                                .headers(h -> cabeceras(h, token))
                                .body(productoIds)
                                .retrieve()
                                .body(new ParameterizedTypeReference<List<LineaPrecio>>() {
                                });
                        return respuesta == null ? List.<LineaPrecio>of() : respuesta;

                    } catch (RestClientException ex) {
                        throw traducir("consultar precios", ex);
                    }
                }));
    }

    /* ── Participante remoto de la saga ── */

    /**
     * Paso 1: aparta el stock. Idempotente por `referencia`, así que el reintento tras un
     * timeout no descuenta dos veces.
     */
    public void reservarStock(String token, String referencia, List<AjusteStock> lineas) {
        Reintentos.conEsperaExponencial("catalogo.reservar", 3, () ->
                cortacircuitos.ejecutar(() -> {
                    try {
                        cliente.post()
                                .uri("/api/inventario/reservas/{ref}", referencia)
                                .headers(h -> cabeceras(h, token))
                                .body(Map.of("lineas", lineas))
                                .retrieve()
                                // Un 409 es falta de stock: es una respuesta de
                                // negocio, no un fallo transitorio, y no se reintenta.
                                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                                    throw new ConflictoException(
                                            "No hay stock suficiente para completar la compra");
                                })
                                .toBodilessEntity();
                        return null;

                    } catch (ConflictoException ex) {
                        throw ex;
                    } catch (RestClientException ex) {
                        throw traducir("reservar stock", ex);
                    }
                }));
    }

    /** Paso final: el pago llegó, la reserva pasa a definitiva. */
    public void confirmarReserva(String token, String referencia) {
        Reintentos.conEsperaExponencial("catalogo.confirmar", 3, () ->
                cortacircuitos.ejecutar(() -> {
                    try {
                        cliente.post()
                                .uri("/api/inventario/reservas/{ref}/confirmar", referencia)
                                .headers(h -> cabeceras(h, token))
                                .retrieve()
                                .toBodilessEntity();
                        return null;

                    } catch (RestClientException ex) {
                        throw traducir("confirmar reserva", ex);
                    }
                }));
    }

    /**
     * Compensación. Se reintenta con más insistencia porque dejar stock bloqueado es peor
     * que una llamada de más; si aun así falla, catálogo lo liberará por caducidad.
     */
    public void liberarReserva(String token, String referencia) {
        try {
            Reintentos.conEsperaExponencial("catalogo.liberar", 4, () ->
                    cortacircuitos.ejecutar(() -> {
                        try {
                            cliente.post()
                                    .uri("/api/inventario/reservas/{ref}/liberar", referencia)
                                    .headers(h -> cabeceras(h, token))
                                    .retrieve()
                                    .toBodilessEntity();
                            return null;

                        } catch (RestClientException ex) {
                            throw traducir("liberar reserva", ex);
                        }
                    }));

        } catch (RuntimeException ex) {
            // La red de seguridad es la caducidad de la reserva en catálogo.
            log.error("No se pudo liberar la reserva {}; caducará sola. Causa: {}",
                    referencia, ex.getMessage());
        }
    }

    /** Propaga el token del usuario y el identificador de correlación. */
    private void cabeceras(org.springframework.http.HttpHeaders h, String token) {
        if (token != null && !token.isBlank()) {
            h.setBearerAuth(token);
        }
        String correlacion = MDC.get("correlacionId");
        if (correlacion != null) {
            h.set("X-Correlation-Id", correlacion);
        }
    }

    private RuntimeException traducir(String operacion, RestClientException ex) {
        log.error("Catálogo falló al {}: {}", operacion, ex.getMessage());
        return new ServicioNoDisponibleException(
                "No pudimos contactar con el catálogo. Intenta de nuevo en unos segundos.");
    }
}
