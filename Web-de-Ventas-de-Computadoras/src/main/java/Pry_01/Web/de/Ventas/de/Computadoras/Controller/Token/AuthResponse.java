package Pry_01.Web.de.Ventas.de.Computadoras.Controller.Token;

import lombok.AllArgsConstructor;
import lombok.Data;


@Data
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String role;
}
