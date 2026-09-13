package com.backend.web_gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * La pasarela entera, arrancada y respondiendo por HTTP.
 *
 * <p>Aquí no hay contenedor y no hace falta: la pasarela no tiene base de datos.
 * Lo que no alcanzan las unitarias es otra cosa: que los filtros estén MONTADOS
 * en la cadena real y en el orden que se cree. {@code RateLimitFilter} se puede
 * probar suelto —y se prueba—, pero eso no dice nada de si Spring lo llama.
 *
 * <p>Los tres destinos apuntan a un puerto donde no escucha nadie. Es
 * deliberado: todo lo que se comprueba aquí ocurre ANTES de reenviar, y así lo
 * que sí llega al proxy falla en el acto en lugar de agotar un tiempo de espera.
 *
 * <p>El cliente es el del JDK a propósito. Bastaría {@code TestRestTemplate},
 * pero en Spring Boot 4 vive en un artefacto aparte y no se va a añadir una
 * dependencia para dos peticiones.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "servicios.catalogo.url=http://localhost:1",
                "servicios.usuarios.url=http://localhost:1",
                "servicios.compras.url=http://localhost:1",
        })
@Tag("integracion")
@DisplayName("La pasarela en marcha")
class PasarelaIT {

    @LocalServerPort
    private int puerto;

    private final HttpClient cliente = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private HttpResponse<String> pedir(String metodo, String ruta)
            throws IOException, InterruptedException {

        HttpRequest.BodyPublisher cuerpo = "POST".equals(metodo)
                ? HttpRequest.BodyPublishers.ofString("{}")
                : HttpRequest.BodyPublishers.noBody();

        HttpRequest peticion = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + puerto + ruta))
                .header("Content-Type", "application/json")
                .method(metodo, cuerpo)
                .timeout(Duration.ofSeconds(10))
                .build();

        return cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("los intentos de acceso se cortan al pasarse del cupo")
    void elCupoDeAccesoSeAplicaEnLaCadenaReal() throws Exception {
        HttpResponse<String> ultima = null;
        for (int i = 0; i < 21; i++) {
            ultima = pedir("POST", "/api/auth/login");
        }

        assertThat(ultima).isNotNull();
        assertThat(ultima.statusCode())
                .as("el filtro esta enganchado de verdad, no solo probado suelto")
                .isEqualTo(429);
        assertThat(ultima.headers().firstValue("Retry-After"))
                .as("se le dice al cliente cuando volver")
                .isPresent();
    }

    @Test
    @DisplayName("navegar no gasta intentos de acceso")
    void lasLecturasDeAuthNoConsumenElCupoDeAcceso() throws Exception {
        /*
         * `/proveedores` se pide al pintar el formulario de acceso y `/yo` en
         * cada carga de pagina. Cuando gastaban el cupo de autenticacion, en un
         * locutorio o una oficina detras de una sola salida a internet la gente
         * se quedaba sin poder entrar por haber navegado. Cuarenta lecturas son
         * el doble del cupo viejo: si alguien vuelve a juntarlos, esto se cae.
         *
         * Responden con un error de pasarela, porque `usuarios` no existe en
         * esta prueba. Lo que importa es que ninguna sea un 429.
         */
        for (int i = 0; i < 40; i++) {
            assertThat(pedir("GET", "/api/auth/proveedores").statusCode())
                    .as("lectura numero %d", i + 1)
                    .isNotEqualTo(429);
        }
    }

    /**
     * El service worker lleva su propia CSP y el resto la del HTML, y cada
     * respuesta lleva UNA. Si llevara las dos, el navegador aplica la
     * intersección y el worker vuelve a quedarse sin poder traer las fotos de
     * producto de otros dominios: el fallo silencioso que esto arregló.
     */
    @Test
    @DisplayName("el service worker recibe su CSP y el índice la suya, nunca las dos")
    void cadaRespuestaLlevaUnaSolaCsp() throws Exception {
        List<String> delWorker = pedir("GET", "/ngsw-worker.js").headers()
                .allValues("Content-Security-Policy");
        List<String> delIndice = pedir("GET", "/").headers()
                .allValues("Content-Security-Policy");

        assertThat(delWorker).hasSize(1);
        assertThat(delWorker.get(0))
                .contains("connect-src 'self' https:")
                .doesNotContain("script-src");

        assertThat(delIndice).hasSize(1);
        assertThat(delIndice.get(0))
                .contains("script-src 'self'")
                .contains("connect-src 'self';");
    }
}
