package Pry_01.Web.de.Ventas.de.Computadoras.Dto.Auth;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class AuthRegisterResponse {
    private Long id;
    private String nombre;
    private String emailAddress;

}
