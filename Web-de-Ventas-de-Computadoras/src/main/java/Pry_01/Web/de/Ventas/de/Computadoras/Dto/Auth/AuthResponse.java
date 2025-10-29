package Pry_01.Web.de.Ventas.de.Computadoras.Dto.Auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String status;
    private String message;
    private String accessToken;
    private String refreshToken;
    private Long usuarioId;
    private String nombre;

    public static AuthResponse success(String message, String accessToken, String refreshToken, Long usuarioId, String nombre) {
        AuthResponse response = new AuthResponse();
        response.setStatus("OK");
        response.setMessage(message);
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setUsuarioId(usuarioId);
        response.setNombre(nombre);
        return response;
    }

    public static AuthResponse error(String message) {
        AuthResponse response = new AuthResponse();
        response.setStatus("ERROR");
        response.setMessage(message);
        return response;
    }

}
