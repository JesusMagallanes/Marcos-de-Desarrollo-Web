package Pry_01.Web.de.Ventas.de.Computadoras.Dto.MarcaDTO;

import java.util.ArrayList;
import java.util.List;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.ProductoModel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MarcaResponseDTO {
    private Long id;
    private String name;
    private Long categoriaId;
    private String categoriaName;
    private List<ProductoModel> productos = new ArrayList<>();
}
