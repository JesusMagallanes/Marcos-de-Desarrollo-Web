package Pry_01.Web.de.Ventas.de.Computadoras.Dto.CategoriaDTO;

import java.util.ArrayList;
import java.util.List;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.ProductoModel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CategoriaResponseDTO {

    private Long id;

    private String name;

    private String urlImage;

    private String description;

    private List<ProductoModel> productos = new ArrayList<>();
}
