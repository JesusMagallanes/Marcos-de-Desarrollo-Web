package Pry_01.Web.de.Ventas.de.Computadoras.Dto.UsuarioDTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.Roles;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Builder
@Data

public class UsuarioCreateDTO {

    @NotBlank
    @Size(min = 2, max = 50)
    @Pattern(regexp = "[a-zA-ZáéíóúÁÉÍÓÚñÑ ]+", message = "El nombre solo debe contener letras y espacios")
    private String name;

    @NotBlank
    @Pattern(regexp = "[a-zA-ZáéíóúÁÉÍÓÚñÑ ]+", message = "El apellido solo debe contener letras y espacios")
    private String lastname;

    @NotBlank
    @Email
    private String emailAddress;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank
    @Size(min = 8)
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{8,}$")
    private String password;

    @NotBlank
    @Pattern(regexp = "\\d{9}")
    private String phoneNumber;

    @NotBlank
    private String address;

    @Builder.Default
    private Roles rol = Roles.CLIENTE;
}
