package Pry_01.Web.de.Ventas.de.Computadoras.Dto.CategoriaDTO;

import java.util.ArrayList;
import java.util.List;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.ProductoModel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor

public class CategoriaCreateDTO {

    @NotBlank(message = "El nombre de la categoría es obligatorio.")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres.")
    private String name;

    @NotBlank
    private String urlImage;
    
    private String slug;

    @NotBlank(message = "La descripción es obligatoria.")
    @Size(max = 500, message = "La descripción no puede exceder 500 caracteres.")

    private String description;

    private List<ProductoModel> productos = new ArrayList<>();

}
