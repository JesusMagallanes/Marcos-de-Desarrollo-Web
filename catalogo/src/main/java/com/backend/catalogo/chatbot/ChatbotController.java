package com.backend.catalogo.chatbot;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backend.catalogo.chatbot.dto.ChatbotDtos.MensajeRequest;
import com.backend.catalogo.chatbot.dto.ChatbotDtos.RespuestaChat;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService servicio;

    @PostMapping("/mensaje")
    /**
     * Público. Si llega con sesión, el token se usa para una sola cosa: que el
     * asistente pueda consultar los pedidos de ESA persona en {@code compras},
     * con los mismos permisos que tendría ella.
     */
    public RespuestaChat mensaje(@Valid @RequestBody MensajeRequest peticion,
            @AuthenticationPrincipal Jwt jwt) {
        return servicio.responder(peticion.mensaje(), peticion.historialOVacio(),
                jwt == null ? null : jwt.getTokenValue());
    }
}
