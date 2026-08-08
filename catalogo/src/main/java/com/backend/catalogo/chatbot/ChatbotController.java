package com.backend.catalogo.chatbot;

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
    public RespuestaChat mensaje(@Valid @RequestBody MensajeRequest peticion) {
        return servicio.responder(peticion.mensaje());
    }
}
