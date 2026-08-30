package Pry_01.Web.de.Ventas.de.Computadoras.Service;

import Pry_01.Web.de.Ventas.de.Computadoras.Security.PromptSecurityFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GroqService {

    @Value("${groq.api.key:#{null}}")
    private String apiKey;

    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqUrl;

    @Value("${groq.model:openai/gpt-oss-20b}")
    private String model;

    @Value("${groq.max.tokens:500}")
    private int maxTokens;

    @Value("${groq.temperature:0.3}")
    private double temperature;

    @Value("${groq.timeout:30000}")
    private int timeout;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PromptSecurityFilter securityFilter;

    public GroqService(PromptSecurityFilter securityFilter) {
        this.securityFilter = securityFilter;
    }

    public String procesarPregunta(String pregunta) {
        System.out.println(" API Key: " + (apiKey != null ? "Configurada " : "No configurada "));
        System.out.println(" Modelo: " + model);
        System.out.println(" Temperatura: " + temperature);
        System.out.println(" Max tokens: " + maxTokens);

        if (apiKey == null || apiKey.isEmpty()) {
            return " No hay API Key de Groq configurada.";
        }

        try {
            System.out.println(" GROQ - Procesando: " + pregunta);

            String sanitizedMessage;
            try {
                sanitizedMessage = securityFilter.sanitizarMensaje(pregunta);
            } catch (SecurityException e) {
                log.warn(" Intento de inyección detectado: {}", e.getMessage());
                return "️ No puedo procesar esa solicitud por razones de seguridad.";
            }

            String prompt = construirPrompt(sanitizedMessage);
            String respuestaGroq = llamarGroq(prompt);
            System.out.println(" GROQ - Respuesta recibida: " + respuestaGroq);

            return respuestaGroq;

        } catch (Exception e) {
            System.out.println(" GROQ - Error: " + e.getMessage());
            e.printStackTrace();
            return " Error al procesar tu pregunta: " + e.getMessage();
        }
    }

    private String construirPrompt(String pregunta) {
        return """
            Eres un asistente virtual de la tienda de tecnología "SmartZone".
            
            === INFORMACIÓN DE LA TIENDA ===
            - SmartZone vende: laptops, celulares y consolas .
            - Las ofertas están en la sección "Ofertas" de la web.
            - Los envíos son a todo el país (3-5 días hábiles).
            - Aceptamos: Visa, Mastercard, Yape, Plin y transferencias bancarias.
            - Contacto: +51 905 187 817 (WhatsApp) o servicioalcliente@gmail.com
            
            === REGLAS ===
            - Responde de manera amable y profesional.
            - Mantén respuestas concisas (máximo 3-4 oraciones).
            - Si el usuario pregunta por productos, recomienda los más populares.
            - Si pregunta por ofertas, menciona que están en la web.
            - Si pregunta por contacto, dale el número y email.
            - SIEMPRE responde en español.
            
            Pregunta del usuario: """ + pregunta;
    }

    private String llamarGroq(String prompt) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        //PARAMETRIZADO
        requestBody.put("model", model);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", "Eres un asistente útil y preciso."),
                Map.of("role", "user", "content", prompt)
        ));
        requestBody.put("temperature", temperature);
        requestBody.put("max_tokens", maxTokens);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                groqUrl,
                HttpMethod.POST,
                entity,
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Error en Groq: " + response.getStatusCode());
        }

        JsonNode json = objectMapper.readTree(response.getBody());
        return json.get("choices").get(0).get("message").get("content").asText();
    }

    public String procesarPregunta(String pregunta, String sessionId) {
        log.info(" Procesando pregunta - Sesión: {}", sessionId);
        return procesarPregunta(pregunta);
    }
}