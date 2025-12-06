package Pry_01.Web.de.Ventas.de.Computadoras.Dto.MarcaDTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MarcaCreateDTO {

    @NotBlank(message = "El nombre de la marca es obligatorio")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
    private String name;

    @NotBlank(message = "La descripción es obligatoria")
    @Size(max = 500, message = "La descripción no puede exceder 500 caracteres")
    private String descripcion;

    @NotNull(message = "Debe especificar la categoría")
    private Long categoriaId;
}
