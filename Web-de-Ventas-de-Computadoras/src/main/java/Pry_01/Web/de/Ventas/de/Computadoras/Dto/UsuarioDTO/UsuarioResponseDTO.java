package Pry_01.Web.de.Ventas.de.Computadoras.Dto.UsuarioDTO;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.Roles;
import lombok.Data;

@Data
public class UsuarioResponseDTO {

    private Long id;
    private String name;
    private String lastname;
    private String emailAddress;
    private String password;
    private String phoneNumber;
    private String address;
    private Roles rol = Roles.CLIENTE;
}
