package Pry_01.Web.de.Ventas.de.Computadoras.Dto.Auth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

public class AuthRegisterResponse {
    private Long id;
    private String nombre;
    private String emailAddress;
    public AuthRegisterResponse(Long id, String nombre, String emailAddress) {
        this.id = id;
        this.nombre = nombre;
        this.emailAddress = emailAddress;
    }

}
