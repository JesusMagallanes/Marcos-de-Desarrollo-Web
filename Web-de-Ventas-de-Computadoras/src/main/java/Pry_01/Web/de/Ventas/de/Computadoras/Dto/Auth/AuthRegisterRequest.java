package Pry_01.Web.de.Ventas.de.Computadoras.Dto.Auth;

import lombok.Data;

@Data
public class AuthRegisterRequest {
    private String nombre;
    private String apellido;
    private String emailAddress;
    private String password;
    private String telefono;
    private String direccion;
}
