package Pry_01.Web.de.Ventas.de.Computadoras.Dto.UsuarioDTO;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.Roles;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UsuarioUpdateDTO {
    
    @Size(min = 2, max = 50)
    @Pattern(regexp = "[a-zA-ZáéíóúÁÉÍÓÚñÑ ]+", message = "El nombre solo debe contener letras y espacios")
    private String name;

    @Pattern(regexp = "[a-zA-ZáéíóúÁÉÍÓÚñÑ ]+", message = "El apellido solo debe contener letras y espacios")
    private String lastname;

    @Email
    private String emailAddress;

    @Pattern(regexp = "\\d{9}")
    private String phoneNumber;

    private String address;

    @Builder.Default
    private Roles rol = Roles.CLIENTE;
}
