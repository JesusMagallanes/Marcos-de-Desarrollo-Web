package Pry_01.Web.de.Ventas.de.Computadoras.Controller.Token;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import Pry_01.Web.de.Ventas.de.Computadoras.Configuration.Jwt.JwtUtil;
import Pry_01.Web.de.Ventas.de.Computadoras.Configuration.User.UsuarioDetailsService;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.UsuarioService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UsuarioDetailsService userDetailsService;
    private final UsuarioService usuarioService;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(), request.getPassword()
                )
        );

        UsuarioModel usuario = usuarioService.obtenerPorEmail(request.getEmail());

        UserDetails user = userDetailsService.loadUserByUsername(usuario.getEmailAddress());

        String accessToken = jwtUtil.generateAccessToken(
                usuario.getEmailAddress(),
                usuario.getRol()
        );

        String refreshToken = jwtUtil.generateRefreshToken(usuario.getEmailAddress());

        return ResponseEntity.ok(new AuthResponse(accessToken, refreshToken, usuario.getRol().name()));
    }
}
