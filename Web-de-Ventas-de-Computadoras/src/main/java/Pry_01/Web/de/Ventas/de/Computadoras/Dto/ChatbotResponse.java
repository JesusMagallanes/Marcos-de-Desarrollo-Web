package Pry_01.Web.de.Ventas.de.Computadoras.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotResponse {

    private boolean success;
    private String respuesta;
    private String error;
    private List<Map<String, Object>> productos;
    private String sessionId;
    private Long timestamp;

    public static ChatbotResponse success(String respuesta) {
        return ChatbotResponse.builder()
                .success(true)
                .respuesta(respuesta)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static ChatbotResponse success(String respuesta, List<Map<String, Object>> productos) {
        return ChatbotResponse.builder()
                .success(true)
                .respuesta(respuesta)
                .productos(productos)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static ChatbotResponse error(String mensaje) {
        return ChatbotResponse.builder()
                .success(false)
                .error(mensaje)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}