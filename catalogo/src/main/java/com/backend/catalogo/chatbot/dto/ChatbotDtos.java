package com.backend.catalogo.chatbot.dto;

import java.util.List;

import com.backend.catalogo.producto.dto.ProductoDtos.ProductoResponse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ChatbotDtos {

    private ChatbotDtos() {
    }

    public record MensajeRequest(
            @NotBlank @Size(max = 500, message = "El mensaje es demasiado largo") String mensaje) {
    }

    public record RespuestaChat(
            String respuesta,
            String tipo,
            List<ProductoResponse> productos,
            String categoria) {
    }
}
