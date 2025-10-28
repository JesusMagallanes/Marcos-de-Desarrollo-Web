package Pry_01.Web.de.Ventas.de.Computadoras.Dto.ProductosDTO;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor

public class ProductosResponseDTO {
    private final Long id;

    private final String name;

    private final String description;

    private final double precio;

    private final String imageUrl;

    private final Integer stock;

    private final String categoriaName;
}
