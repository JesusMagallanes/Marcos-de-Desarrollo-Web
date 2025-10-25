package Pry_01.Web.de.Ventas.de.Computadoras.Dto.MetodoPagoDTO;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class MetodoPagoCreateDTO {

    @Size(max = 50, message = "El nombre no puede tener más de 50 caracteres.")
    private String name;

    @Size(max = 200, message = "La descripción no puede tener más de 200 caracteres.")
    private String descripcion;

}
