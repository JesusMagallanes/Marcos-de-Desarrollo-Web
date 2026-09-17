package com.backend.catalogo.shared.ia;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.json.JsonMapper;

/**
 * El cliente contra un servidor HTTP de verdad que habla como Groq.
 *
 * <p>Sin simulacros del cliente HTTP: así se prueba también que la petición sale
 * con la forma que espera el proveedor y que la respuesta se lee con los
 * convertidores que Spring registra de verdad.
 */
@DisplayName("Cliente del modelo de lenguaje")
class ClienteIaTest {

    private HttpServer servidor;
    private final Deque<Respuesta> respuestas = new ArrayDeque<>();
    private final AtomicReference<String> ultimoCuerpo = new AtomicReference<>();
    private final AtomicReference<String> ultimaAutorizacion = new AtomicReference<>();
    private final AtomicReference<String> ultimaLongitud = new AtomicReference<>();
    private SimpleMeterRegistry metricas;

    private record Respuesta(int estado, String cuerpo) {
    }

    @BeforeEach
    void arrancar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/chat", intercambio -> {
            ultimoCuerpo.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            ultimaAutorizacion.set(intercambio.getRequestHeaders().getFirst("Authorization"));
            ultimaLongitud.set(intercambio.getRequestHeaders().getFirst("Content-Length"));
            Respuesta r = respuestas.isEmpty() ? new Respuesta(500, "{}") : respuestas.poll();
            byte[] bytes = r.cuerpo().getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(r.estado(), bytes.length);
            intercambio.getResponseBody().write(bytes);
            intercambio.close();
        });
        servidor.start();
        metricas = new SimpleMeterRegistry();
    }

    @AfterEach
    void parar() {
        servidor.stop(0);
    }

    private ClienteIa cliente(String clave) {
        return new ClienteIa(JsonMapper.builder().build(), metricas, clave,
                "http://127.0.0.1:" + servidor.getAddress().getPort() + "/chat",
                "modelo-de-prueba", "modelo-vision", 0.2, Duration.ofSeconds(2));
    }

    private static String completado(String contenido) {
        String escapado = contenido.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"" + escapado + "\"}}]}";
    }

    private double contador(String resultado) {
        var c = metricas.find("smartzone_ia_total").tag("resultado", resultado).counter();
        return c == null ? 0 : c.count();
    }

    @Test
    @DisplayName("sin clave no llama a nadie y lo cuenta")
    void sinClave() {
        ClienteIa ia = cliente("  ");

        assertThat(ia.disponible()).isFalse();
        assertThat(ia.json("chat", "s", "u", 10)).isEmpty();
        assertThat(ultimoCuerpo.get()).isNull();
        assertThat(contador("no_configurada")).isEqualTo(1);
    }

    @Test
    @DisplayName("pide modo JSON con el modelo y la clave configurados, y lee el objeto")
    void jsonCorrecto() {
        respuestas.add(new Respuesta(200, completado("{\"respuesta\":\"hola\"}")));

        var r = cliente("clave-secreta").json("chat", "sistema", "usuario", 50);

        assertThat(r).isPresent();
        assertThat(r.get().path("respuesta").asString()).isEqualTo("hola");
        assertThat(ultimaAutorizacion.get()).isEqualTo("Bearer clave-secreta");
        // Sin Content-Length el cuerpo iba troceado y hay proveedores que lo rechazan.
        assertThat(ultimaLongitud.get()).isNotNull()
                .isEqualTo(String.valueOf(ultimoCuerpo.get().getBytes(StandardCharsets.UTF_8).length));
        assertThat(ultimoCuerpo.get())
                .contains("\"model\":\"modelo-de-prueba\"")
                .contains("\"response_format\":{\"type\":\"json_object\"}")
                .contains("\"max_tokens\":50");
        assertThat(contador("ok")).isEqualTo(1);
    }

    @Test
    @DisplayName("con imagen: usa el modelo de visión y la manda incrustada, nunca como enlace")
    void conImagen() {
        respuestas.add(new Respuesta(200, completado("{\"ok\":true}")));

        var r = cliente("k").jsonConImagen("foto", "s", "¿qué es?", new byte[] { 1, 2, 3 }, "image/png", 50);

        assertThat(r).isPresent();
        assertThat(ultimoCuerpo.get())
                .contains("\"model\":\"modelo-vision\"")
                .contains("data:image/png;base64,AQID")
                .contains("\"type\":\"image_url\"");
    }

    @Test
    @DisplayName("un contenido que no es un objeto JSON es un fallo, no una respuesta")
    void jsonInvalido() {
        respuestas.add(new Respuesta(200, completado("claro, aqui tienes: [1,2]")));
        respuestas.add(new Respuesta(200, completado("[1,2,3]")));
        ClienteIa ia = cliente("k");

        assertThat(ia.json("chat", "s", "u", 10)).isEmpty();
        assertThat(ia.json("chat", "s", "u", 10)).isEmpty();
        assertThat(contador("invalida")).isEqualTo(2);
    }

    @Test
    @DisplayName("tras tres fallos seguidos deja de llamar un rato")
    void cortacircuitos() {
        ClienteIa ia = cliente("k");
        for (int i = 0; i < 3; i++) {
            respuestas.add(new Respuesta(503, "{\"error\":\"caido\"}"));
            assertThat(ia.texto("chat", "s", "u", 10)).isEmpty();
        }
        ultimoCuerpo.set(null);
        respuestas.add(new Respuesta(200, completado("ya funciono")));

        assertThat(ia.texto("chat", "s", "u", 10)).isEmpty();
        assertThat(ultimoCuerpo.get()).as("con el circuito abierto no sale la peticion").isNull();
        assertThat(contador("error")).isEqualTo(3);
        assertThat(contador("circuito_abierto")).isEqualTo(1);
    }
}
