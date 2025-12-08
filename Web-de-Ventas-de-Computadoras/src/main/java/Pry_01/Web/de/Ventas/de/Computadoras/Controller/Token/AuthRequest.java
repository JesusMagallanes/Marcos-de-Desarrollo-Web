package Pry_01.Web.de.Ventas.de.Computadoras.Controller.Token;

import lombok.Data;

@Data
public class AuthRequest {
    private String email;
    private String password;
}
