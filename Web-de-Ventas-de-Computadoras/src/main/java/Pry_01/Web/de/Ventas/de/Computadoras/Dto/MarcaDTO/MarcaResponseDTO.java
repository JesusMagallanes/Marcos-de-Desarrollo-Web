package Pry_01.Web.de.Ventas.de.Computadoras.Dto.MarcaDTO;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MarcaResponseDTO {
    private Long id;
    private String nombre;
    private Long categoriaId;
}
