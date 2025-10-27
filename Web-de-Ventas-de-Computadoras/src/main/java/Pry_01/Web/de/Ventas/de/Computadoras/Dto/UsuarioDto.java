package Pry_01.Web.de.Ventas.de.Computadoras.Dto;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.Roles;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UsuarioDto {
    
    private Long id;
    private String name;
    private String lastname;

    @Email
    private String emailAddress;

    @Pattern(regexp = "\\d{9}", message = "El teléfono debe tener exactamente 9 dígitos.")
    private String phoneNumber;

    private String address;

    private Roles rol;

    private String password; 


}