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

@Setter
@Getter
@NoArgsConstructor
public class MarcaUpdateDTO {
    
    private Long id;
    
    @NotBlank(message = "El nombre no puede estar vacío.")
    @Size(min = 2, max = 50, message = "El nombre debe tener entre 2 y 50 caracteres.")
    private String name;

    @NotNull(message = "El producto debe pertenecer a una categoría.")
    private Long categoriaId;

    private List<ProductoModel> productos = new ArrayList<>();
}

