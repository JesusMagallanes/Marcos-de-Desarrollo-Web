package Pry_01.Web.de.Ventas.de.Computadoras.Dto.CategoriaDTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CategoriaCreateDTO {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotBlank
    private String urlImage;

    private String slug;

    @NotBlank
    @Size(max = 500)
    private String description;
}
