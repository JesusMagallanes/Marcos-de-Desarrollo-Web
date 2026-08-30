package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import Pry_01.Web.de.Ventas.de.Computadoras.Dto.ChatbotRequest;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.ChatbotResponse;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.GroqService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatBotController {

    private final GroqService groqService;


    @PostMapping("/mensaje")
    public ResponseEntity<ChatbotResponse> procesarMensaje(
            @Valid @RequestBody ChatbotRequest request,
            HttpSession session) {

        // Obtener o crear sessionId
        String sessionId = (String) session.getAttribute("chatSessionId");
        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString();
            session.setAttribute("chatSessionId", sessionId);
        }

        log.info(" Mensaje recibido - Sesión: {}, Mensaje: {}", sessionId, request.getMensaje());

        try {
            // Procesar con GROQ
            String respuesta = groqService.procesarPregunta(request.getMensaje(), sessionId);
            return ResponseEntity.ok(ChatbotResponse.success(respuesta));

        } catch (Exception e) {
            log.error(" Error en ChatbotController - Sesión: {}", sessionId, e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ChatbotResponse.error("Error al procesar el mensaje: " + e.getMessage()));
        }
    }


    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("status", "Chatbot funcionando correctamente 🚀");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }


    @PostMapping("/clear-session")
    public ResponseEntity<ChatbotResponse> clearSession(HttpSession session) {
        session.removeAttribute("chatSessionId");
        return ResponseEntity.ok(ChatbotResponse.success("Sesión limpiada exitosamente"));
    }
}