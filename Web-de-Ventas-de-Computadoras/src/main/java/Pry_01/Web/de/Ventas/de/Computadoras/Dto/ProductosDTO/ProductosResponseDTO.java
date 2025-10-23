package Pry_01.Web.de.Ventas.de.Computadoras.Dto.ProductosDTO;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;


public class ProductosResponseDTO {
    private Long id;

    private String name;

    private String description;

    @Positive(message = "El precio debe ser mayor que 0.")
    private double precio;

    private String imageUrl;

    @Min(value = 0, message = "El stock no puede ser negativo.")
    private Integer stock;

    private Long categoriaId;
}
