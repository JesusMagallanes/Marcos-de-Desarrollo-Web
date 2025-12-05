package Pry_01.Web.de.Ventas.de.Computadoras.Dto.MarcaDTO;

import java.util.ArrayList;
import java.util.List;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.ProductoModel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MarcaCreateDTO {
    @NotBlank(message = "El nombre de la categoría es obligatorio.")
    @Size(max = 100, message = "El nombre no puede exceder 100 caracteres.")
    private String name;

    @NotNull(message = "El producto debe pertenecer a una categoría.")
    private Long categoriaId;
    
    private List<ProductoModel> productos = new ArrayList<>();
}
