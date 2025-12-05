package Pry_01.Web.de.Ventas.de.Computadoras.Dto.MarcaDTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MarcaUpdateDTO {
    @NotBlank
    private String nombre;

    @NotNull
    private Long categoriaId;
}
