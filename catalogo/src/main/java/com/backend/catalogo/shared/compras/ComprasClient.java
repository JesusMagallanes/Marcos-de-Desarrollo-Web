package com.backend.catalogo.shared.compras;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import lombok.extern.slf4j.Slf4j;

/**
 * Lo que este servicio le pregunta a `compras`: si alguien compró un producto
 * (para valorarlo) y, para el chat, sus propios pedidos.
 *
 * <p><b>Por qué existe.</b> Solo quien compró un producto puede valorarlo, y la
 * única fuente de verdad de eso son los pedidos, que viven en el otro servicio.
 * Sin la comprobación, cualquiera con una cuenta podía puntuar cualquier cosa
 * sin haberla comprado nunca: es como se llenan de resenias falsas las tiendas.
 *
 * <p><b>Se reenvía el token del propio usuario</b>, no uno de servicio. La
 * pregunta es literalmente «¿compré yo esto?», así que la responde `compras`
 * mirando los pedidos de quien pregunta. Con un token de servicio habría que
 * mandar el id del usuario por la URL, y entonces cualquiera podría preguntar
 * por las compras de otro.
 *
 * <p><b>Ante la duda, NO se deja valorar.</b> Si `compras` no responde no se
 * puede saber si hubo compra, y dar por buena una resenia sin comprobar es
 * justo lo que se quiere evitar. Es la decisión contraria a la del correo, y por
 * el mismo motivo: alli el fallo no puede deshacer una aprobación ya hecha; aqui
 * el fallo no puede conceder un permiso que no consta.
 */
@Component
@Slf4j
public class ComprasClient {

    private final RestClient cliente;

    /**
     * Se construye con `RestClient.builder()` y no inyectando el
     * `RestClient.Builder` de Spring: ese bean no existe en este servicio, y
     * pedirlo impedia arrancar entero.
     *
     * <p>Los tiempos de espera son explicitos y cortos. Sin ellos, una caida de
     * `compras` dejaria colgada cada peticion de valoracion hasta agotar el pool
     * de hilos: la tienda entera se quedaria sin responder por no poder
     * comprobar una resenia.
     */
    public ComprasClient(@Value("${servicios.compras.url:http://localhost:8083}") String comprasUrl) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(3));
        fabrica.setReadTimeout(Duration.ofSeconds(5));

        this.cliente = RestClient.builder()
                .baseUrl(comprasUrl)
                .requestFactory(fabrica)
                .build();
    }

    /**
     * @param token el JWT del usuario que quiere valorar
     * @return true solo si consta una compra pagada de ese producto
     */
    public boolean comproElProducto(String token, Long productoId) {
        if (token == null || token.isBlank()) {
            return false;
        }

        try {
            Map<?, ?> respuesta = cliente.get()
                    .uri("/api/pedidos/compre/{id}", productoId)
                    .headers(h -> {
                        h.setBearerAuth(token);
                        // Para poder seguir una peticion entre los dos servicios.
                        String correlacion = MDC.get("correlacionId");
                        if (correlacion != null) {
                            h.set("X-Correlation-Id", correlacion);
                        }
                    })
                    .retrieve()
                    .body(Map.class);

            return respuesta != null && Boolean.TRUE.equals(respuesta.get("comprado"));

        } catch (RestClientException ex) {
            log.warn("No se pudo comprobar la compra del producto {}: {}", productoId, ex.getMessage());
            return false;
        }
    }

    /**
     * Los pedidos y envíos de quien pregunta, en bruto, para el asistente del chat.
     *
     * <p>Con el token de la propia persona: {@code compras} aplica sus permisos y
     * su RLS, así que es imposible que el chat vea pedidos ajenos. Vacío si
     * {@code compras} no responde; el asistente lo dice en vez de inventar.
     */
    public Optional<PedidosDelCliente> pedidosDe(String token) {
        try {
            List<Map<String, Object>> pedidos = cliente.get()
                    .uri("/api/pedidos/mios")
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() { });
            List<Map<String, Object>> envios = cliente.get()
                    .uri("/api/envios/mios")
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() { });
            return Optional.of(new PedidosDelCliente(
                    pedidos == null ? List.of() : pedidos,
                    envios == null ? List.of() : envios));
        } catch (RestClientException ex) {
            log.warn("No se pudieron consultar los pedidos para el chat: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public record PedidosDelCliente(List<Map<String, Object>> pedidos,
            List<Map<String, Object>> envios) {
    }
}
