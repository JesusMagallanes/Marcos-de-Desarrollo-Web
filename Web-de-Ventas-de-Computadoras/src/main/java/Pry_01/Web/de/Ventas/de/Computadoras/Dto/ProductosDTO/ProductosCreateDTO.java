package Pry_01.Web.de.Ventas.de.Computadoras.Dto.ProductosDTO;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProductosCreateDTO {

    @NotBlank(message = "El nombre no puede estar vacío.")
    @Size(min = 2, max = 50, message = "El nombre debe tener entre 2 y 50 caracteres.")
    private String name;

    @NotBlank(message = "La descripción no puede estar vacía.")
    @Size(min = 10, max = 500, message = "La descripción debe tener entre 10 y 500 caracteres.")
    private String description;

    @Positive(message = "El precio debe ser mayor que 0.")
    private double precio;

    @NotBlank(message = "La URL de la imagen no puede estar vacía.")
    @Size(max = 255, message = "La URL de la imagen no debe superar los 255 caracteres.")
    private String imageUrl;

    @NotNull(message = "El stock no puede ser nulo.")
    @Min(value = 0, message = "El stock no puede ser negativo.")
    private Integer stock;

    @NotNull(message = "El producto debe pertenecer a una categoría.")
    private Long categoriaId;

    @NotNull(message = "El producto debe pertenecer a una marca.")
    private Long marcaId;
}
