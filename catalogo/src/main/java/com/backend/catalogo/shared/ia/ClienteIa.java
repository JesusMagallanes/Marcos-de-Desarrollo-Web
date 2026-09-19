package com.backend.catalogo.shared.ia;

import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * El único sitio del servicio que habla con un modelo de lenguaje.
 *
 * <p>Habla el protocolo de chat de OpenAI, que es el que expone Groq —el
 * proveedor que eligió el equipo en la rama {@code mobile}— y también OpenAI,
 * Together o un Ollama local: cambiar de proveedor es cambiar tres variables de
 * entorno, no código.
 *
 * <h4>La IA es una mejora, nunca una dependencia</h4>
 *
 * <p>Sin {@code GROQ_API_KEY} el servicio arranca igual y cada función que usa
 * esto cae a su versión algorítmica. Lo mismo si el proveedor tarda, falla o
 * devuelve algo que no se puede leer: {@link #json} y {@link #texto} devuelven
 * vacío y quien llama decide qué enseñar. Ninguna pantalla de la tienda se rompe
 * porque un tercero no conteste.
 *
 * <h4>Cortacircuitos</h4>
 *
 * <p>Tras tres fallos seguidos se deja de llamar durante un minuto. Sin eso, con
 * el proveedor caído cada mensaje del chat esperaría el timeout completo antes
 * de caer a la respuesta algorítmica, y la tienda parecería lenta sin motivo.
 *
 * <h4>Tope de gasto</h4>
 *
 * <p>El cupo por IP del filtro de rate limit no acota la factura: basta con que
 * lleguen visitantes de muchas IP. Por eso hay además un tope de llamadas por
 * minuto que se cuenta en la base y por tanto vale para todas las réplicas a la
 * vez ({@link PresupuestoIa}): el gasto es uno solo, aunque los procesos sean
 * tres. Al pasarlo, las funciones siguen respondiendo con su versión
 * algorítmica, que es lo mismo que ocurre si el proveedor se cae.
 *
 * <h4>Lo que NUNCA se registra</h4>
 *
 * <p>Ni el prompt ni la respuesta: llevan texto de clientes. En el log y en las
 * métricas solo queda la función, el resultado, el tiempo y cuántos tokens
 * costó.
 *
 * <p>Esta clase es idéntica en {@code catalogo} y {@code usuarios}; el guardián de
 * copias del CI lo vigila.
 */
@Component
@Slf4j
public class ClienteIa {

    /** Nombre de función para las métricas: conjunto cerrado y pequeño. */
    private static final Pattern FUNCION = Pattern.compile("^[a-z_]{1,40}$");

    private static final int FALLOS_PARA_ABRIR = 3;
    private static final Duration PAUSA_CIRCUITO = Duration.ofMinutes(1);

    private final String apiKey;
    private final String modelo;
    private final String modeloVision;
    private final double temperatura;
    private final RestClient cliente;
    private final JsonMapper json;
    private final MeterRegistry registro;

    private final int maxPorMinuto;
    private final PresupuestoIa presupuesto;

    private final AtomicInteger fallosSeguidos = new AtomicInteger();
    private final AtomicLong abiertoHasta = new AtomicLong();

    /*
     * `RestClient.builder()` y no el `RestClient.Builder` de Spring: ese bean no
     * existe en todos los servicios, y esta clase tiene que ser igual en todos.
     */
    public ClienteIa(JsonMapper json, MeterRegistry registro, PresupuestoIa presupuesto,
            @Value("${ia.api-key:}") String apiKey,
            @Value("${ia.api-url:https://api.groq.com/openai/v1/chat/completions}") String apiUrl,
            @Value("${ia.modelo:llama-3.3-70b-versatile}") String modelo,
            @Value("${ia.modelo-vision:meta-llama/llama-4-scout-17b-16e-instruct}") String modeloVision,
            @Value("${ia.temperatura:0.2}") double temperatura,
            @Value("${ia.timeout:8s}") Duration timeout,
            @Value("${ia.max-por-minuto:120}") int maxPorMinuto) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.modelo = modelo;
        this.modeloVision = modeloVision;
        this.temperatura = temperatura;
        this.maxPorMinuto = maxPorMinuto;
        this.presupuesto = presupuesto;
        this.json = json;
        this.registro = registro;
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(3));
        fabrica.setReadTimeout(timeout);
        this.cliente = RestClient.builder()
                .baseUrl(apiUrl)
                .requestFactory(fabrica)
                .build();

        /*
         * El contador de `circuito_abierto` dice cuántas llamadas se rechazaron,
         * pero no si AHORA mismo está abierto: una alerta necesita el estado, no
         * el acumulado.
         */
        Gauge.builder("smartzone_ia_circuito_abierto", this, c -> c.circuitoAbierto() ? 1 : 0)
                .description("1 mientras el cortacircuitos esta abierto y no se llama al modelo")
                .register(registro);
    }

    @PostConstruct
    void avisar() {
        if (disponible()) {
            log.info("IA aplicada activa con el modelo {}", modelo);
        } else {
            log.warn("Sin GROQ_API_KEY: las funciones de IA usan su version algoritmica.");
        }
    }

    /** Si hay proveedor configurado. Lo consulta el frontend para enseñar o no los botones. */
    public boolean disponible() {
        return !apiKey.isEmpty();
    }

    boolean circuitoAbierto() {
        return System.currentTimeMillis() < abiertoHasta.get();
    }

    /** Una respuesta en texto libre. */
    public Optional<String> texto(String funcion, String sistema, String usuario, int maxTokens) {
        Optional<String> respuesta = llamar(funcion, modelo, sistema, usuario, maxTokens, false);
        respuesta.ifPresent(r -> contar(funcion, "ok"));
        return respuesta;
    }

    /**
     * Una respuesta que TIENE que ser un objeto JSON.
     *
     * <p>Se pide en modo JSON y aun así se comprueba: un modelo puede devolver
     * texto, un array o un objeto a medias si se queda sin tokens. Lo que no sea
     * un objeto se trata como un fallo, igual que un 500.
     */
    public Optional<JsonNode> json(String funcion, String sistema, String usuario, int maxTokens) {
        return comoObjeto(funcion, llamar(funcion, modelo, sistema, usuario, maxTokens, true));
    }

    /**
     * Lo mismo, con una imagen que el modelo de visión tiene que mirar.
     *
     * <p>La imagen va incrustada en la petición (data URL), nunca como enlace:
     * así el proveedor no descarga nada de ningún sitio y no hay forma de usar
     * esto para que un tercero haga peticiones en nombre de la tienda.
     *
     * @param mime {@code image/jpeg}, {@code image/png} o {@code image/webp}
     */
    public Optional<JsonNode> jsonConImagen(String funcion, String sistema, String usuario,
            byte[] imagen, String mime, int maxTokens) {
        List<Map<String, Object>> contenido = List.of(
                Map.of("type", "text", "text", usuario),
                Map.of("type", "image_url", "image_url", Map.of("url",
                        "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(imagen))));
        return comoObjeto(funcion, llamar(funcion, modeloVision, sistema, contenido, maxTokens, true));
    }

    /*
     * El «ok» se cuenta aquí y no en `llamar`: una respuesta que llega pero no se
     * puede leer no es un acierto. Contándolo antes, una función rota por
     * formato salía en las métricas como ok Y como invalida a la vez, y el
     * porcentaje de aciertos del panel daba de más.
     */
    private Optional<JsonNode> comoObjeto(String funcion, Optional<String> respuesta) {
        return respuesta.flatMap(contenido -> {
            try {
                JsonNode nodo = json.readTree(contenido);
                if (nodo != null && nodo.isObject()) {
                    contar(funcion, "ok");
                    return Optional.of(nodo);
                }
            } catch (RuntimeException ilegible) {
                // cae abajo
            }
            contar(funcion, "invalida");
            return Optional.empty();
        });
    }

    private Optional<String> llamar(String funcion, String modeloUsado, String sistema,
            Object usuario, int maxTokens, boolean modoJson) {
        if (!FUNCION.matcher(funcion).matches()) {
            throw new IllegalArgumentException("Nombre de funcion de IA no valido: " + funcion);
        }
        if (!disponible()) {
            contar(funcion, "no_configurada");
            return Optional.empty();
        }
        if (circuitoAbierto()) {
            contar(funcion, "circuito_abierto");
            return Optional.empty();
        }
        if (!presupuesto.hayCupo(maxPorMinuto)) {
            contar(funcion, "sin_presupuesto");
            return Optional.empty();
        }

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("model", modeloUsado);
        cuerpo.put("temperature", temperatura);
        cuerpo.put("max_tokens", maxTokens);
        cuerpo.put("messages", List.of(
                Map.of("role", "system", "content", sistema),
                Map.of("role", "user", "content", usuario)));
        if (modoJson) {
            cuerpo.put("response_format", Map.of("type", "json_object"));
        }

        Timer.Sample reloj = Timer.start(registro);
        try {
            /*
             * Serializado aquí y con longitud fija. Pasando el mapa, el cliente lo
             * escribía en streaming con `Transfer-Encoding: chunked` y sin
             * `Content-Length`, y hay proveedores y proxies que rechazan eso: la
             * IA fallaba siempre y todo caía a reglas sin que se viera por qué.
             */
            byte[] peticion = json.writeValueAsBytes(cuerpo);
            JsonNode respuesta = cliente.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .contentLength(peticion.length)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(peticion)
                    .retrieve()
                    .body(JsonNode.class);

            String contenido = respuesta == null ? null
                    : respuesta.path("choices").path(0).path("message").path("content").asString(null);
            if (contenido == null || contenido.isBlank()) {
                throw new IllegalStateException("respuesta sin contenido");
            }
            fallosSeguidos.set(0);
            anotarTokens(funcion, respuesta);
            return Optional.of(contenido.trim());

        } catch (RuntimeException fallo) {
            if (fallosSeguidos.incrementAndGet() >= FALLOS_PARA_ABRIR) {
                abiertoHasta.set(System.currentTimeMillis() + PAUSA_CIRCUITO.toMillis());
                fallosSeguidos.set(0);
                log.warn("IA: {} fallos seguidos, se deja de llamar durante {}",
                        FALLOS_PARA_ABRIR, PAUSA_CIRCUITO);
            }
            // Solo el tipo: el mensaje de un cliente HTTP puede incluir el cuerpo.
            log.warn("IA: fallo en '{}' ({})", funcion, fallo.getClass().getSimpleName());
            contar(funcion, "error");
            return Optional.empty();

        } finally {
            /*
             * Con cubos y no solo suma y máximo: el promedio esconde justo lo que
             * importa —la visita lenta— y sin cubos no se puede calcular un
             * percentil en Prometheus. Los cortes son los que se miran al decidir
             * si una función sigue mereciendo la pena.
             */
            reloj.stop(Timer.builder("smartzone_ia_segundos")
                    .description("Tiempo de las llamadas al modelo de lenguaje")
                    .tag("funcion", funcion)
                    .serviceLevelObjectives(Duration.ofMillis(500), Duration.ofSeconds(1),
                            Duration.ofSeconds(2), Duration.ofSeconds(4), Duration.ofSeconds(8))
                    .register(registro));
        }
    }

    /**
     * Lo que costó la llamada, en tokens. Es lo único que se puede convertir a
     * dinero: sin esto, «la IA gasta mucho» no se puede comprobar ni repartir
     * por función. Un proveedor que no mande {@code usage} simplemente no suma.
     */
    private void anotarTokens(String funcion, JsonNode respuesta) {
        JsonNode uso = respuesta.path("usage");
        sumarTokens(funcion, "entrada", uso.path("prompt_tokens").asInt(0));
        sumarTokens(funcion, "salida", uso.path("completion_tokens").asInt(0));
    }

    private void sumarTokens(String funcion, String tipo, int cantidad) {
        if (cantidad > 0) {
            Counter.builder("smartzone_ia_tokens_total")
                    .description("Tokens consumidos por funcion, para estimar el gasto")
                    .tags("funcion", funcion, "tipo", tipo)
                    .register(registro)
                    .increment(cantidad);
        }
    }

    private void contar(String funcion, String resultado) {
        Counter.builder("smartzone_ia_total")
                .description("Llamadas al modelo de lenguaje por funcion y resultado")
                .tags("funcion", funcion, "resultado", resultado)
                .register(registro)
                .increment();
    }
}
