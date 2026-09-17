package com.backend.catalogo.chatbot.dto;

import java.util.List;

import com.backend.catalogo.producto.dto.ProductoDtos.ProductoResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ChatbotDtos {

    private ChatbotDtos() {
    }

    /**
     * @param historial los últimos turnos de la conversación, para que «¿y en
     *     16 GB?» se entienda. Lo manda el navegador, así que se trata como
     *     datos de terceros: no se confía en que lo que dice «asistente» lo haya
     *     dicho de verdad el asistente. Opcional.
     */
    public record MensajeRequest(
            @NotBlank @Size(max = 500, message = "El mensaje es demasiado largo") String mensaje,
            @Size(max = 8, message = "Como máximo 8 turnos de historial") List<@Valid TurnoChat> historial) {

        public List<TurnoChat> historialOVacio() {
            return historial == null ? List.of() : historial;
        }
    }

    public record TurnoChat(
            @NotBlank @Pattern(regexp = "usuario|asistente") String autor,
            @NotBlank @Size(max = 800) String texto) {
    }

    public record RespuestaChat(
            String respuesta,
            String tipo,
            List<ProductoResponse> productos,
            String categoria) {
    }
}
