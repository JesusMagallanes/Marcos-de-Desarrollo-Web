package Pry_01.Web.de.Ventas.de.Computadoras.Dto.ProductosDTO;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class ProductosUpdateDTO {

    @NotBlank
    @Size(min = 2, max = 50)
    private String name;

    @NotBlank
    @Size(min = 10, max = 500)
    private String description;

    @Positive
    private double precio;

    @NotBlank
    @Size(max = 255)
    private String imageUrl;

    @NotNull
    @Min(0)
    private Integer stock;

    @NotNull
    private Long marcaId;
}
