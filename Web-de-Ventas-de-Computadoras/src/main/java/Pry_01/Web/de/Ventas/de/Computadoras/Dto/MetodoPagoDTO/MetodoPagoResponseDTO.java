package Pry_01.Web.de.Ventas.de.Computadoras.Dto.MetodoPagoDTO;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MetodoPagoResponseDTO {

    private final Long id;

    private final String name;

    private final String descripcion;
}
