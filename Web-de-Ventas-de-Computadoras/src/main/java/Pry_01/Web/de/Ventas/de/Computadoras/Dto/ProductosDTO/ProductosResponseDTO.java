package Pry_01.Web.de.Ventas.de.Computadoras.Dto.ProductosDTO;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductosResponseDTO {
    
    private Long id;
    private String name;
    private String description;
    private double precio;
    private String imageUrl;
    private Integer stock;
    private List<String> marcas;
    private List<String> categorias;
}
