package Pry_01.Web.de.Ventas.de.Computadoras.Service;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import Pry_01.Web.de.Ventas.de.Computadoras.Configuration.Jwt.JwtUtil;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.Auth.AuthRequest;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.Auth.AuthResponse;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.Roles;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Service
@Slf4j
public class AuthService {
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    
    public AuthResponse login(AuthRequest loginRequest) {
        log.info(" INICIO LOGIN DEBUG ");
        try {
            Optional<UsuarioModel> usuarioOpt = usuarioRepository.findByEmailAddress(loginRequest.getCorreo());

            if (usuarioOpt.isEmpty()) {
                log.warn("Usuario no encontrado: {}", loginRequest.getCorreo());
                return AuthResponse.error("Credenciales inválidas");
            }

            UsuarioModel usuario = usuarioOpt.get();
            boolean passwordMatch = passwordEncoder.matches(loginRequest.getPassword(), usuario.getPassword());

            if (!passwordMatch) {
                log.warn("Contraseña incorrecta para: {}", loginRequest.getCorreo());
                return AuthResponse.error("Credenciales inválidas");
            }

            Roles rol = usuario.getRol(); // Enum único

            String accessToken = jwtUtil.generarAccessToken(usuario.getEmailAddress(), rol);
            String refreshToken = jwtUtil.generarRefreshToken(usuario.getEmailAddress());

            return AuthResponse.success("Login exitoso", accessToken, refreshToken, usuario.getId(), usuario.getName());

        } catch (Exception e) {
            log.error("Error durante login: ", e);
            return AuthResponse.error("Error interno del servidor: " + e.getMessage());
        } finally {
            log.info(" FIN LOGIN DEBUG ");
        }
    }

    public AuthResponse refresh(String refreshToken) {
        try {
            if (jwtUtil.isTokenExpired(refreshToken) || !jwtUtil.isRefreshToken(refreshToken)) {
                return AuthResponse.error("Refresh token inválido o expirado");
            }

            String correo = jwtUtil.extractUsername(refreshToken);
            Optional<UsuarioModel> usuarioOpt = usuarioRepository.findByEmailAddress(correo);

            if (usuarioOpt.isEmpty()) {
                return AuthResponse.error("Usuario no encontrado para refresh");
            }

            UsuarioModel usuario = usuarioOpt.get();
            Roles rol = usuario.getRol();

            String newAccessToken = jwtUtil.generarAccessToken(correo, rol);
            String newRefreshToken = jwtUtil.generarRefreshToken(correo);

            return AuthResponse.success("Token renovado", newAccessToken, newRefreshToken, null, null);

        } catch (Exception e) {
            log.error("Error al refrescar token: ", e);
            return AuthResponse.error("Error al renovar token: " + e.getMessage());
        }
    }

}
