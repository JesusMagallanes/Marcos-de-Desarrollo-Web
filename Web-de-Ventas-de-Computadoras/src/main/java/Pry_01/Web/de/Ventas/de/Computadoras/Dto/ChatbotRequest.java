package Pry_01.Web.de.Ventas.de.Computadoras.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatbotRequest {

    @NotBlank(message = "El mensaje no puede estar vacío")
    @Size(min = 1, max = 500, message = "El mensaje debe tener entre 1 y 500 caracteres")
    private String mensaje;
}