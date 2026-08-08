package com.backend.usuarios.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backend.usuarios.auth.dto.AuthDtos.AuthResponse;
import com.backend.usuarios.auth.dto.AuthDtos.LoginRequest;
import com.backend.usuarios.auth.dto.AuthDtos.RefreshRequest;
import com.backend.usuarios.auth.dto.AuthDtos.RegistroRequest;
import com.backend.usuarios.shared.security.UsuarioAutenticado;
import com.backend.usuarios.usuario.UsuarioService;
import com.backend.usuarios.usuario.dto.UsuarioDtos.UsuarioResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService servicio;
    private final UsuarioService usuarioService;

    /** Perfil del usuario del token. */
    @GetMapping("/yo")
    public UsuarioResponse yo(UsuarioAutenticado autenticado) {
        return usuarioService.obtener(autenticado.id());
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest peticion) {
        return servicio.login(peticion);
    }

    @PostMapping("/registrar")
    public ResponseEntity<AuthResponse> registrar(@Valid @RequestBody RegistroRequest peticion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.registrar(peticion));
    }

    /**
     * Canjea un refresh token por un par nuevo. El anterior queda revocado, de modo que un
     * token robado solo sirve una vez y su reúso se detecta.
     */
    @PostMapping("/refresh")
    public AuthResponse refrescar(@Valid @RequestBody RefreshRequest peticion) {
        return servicio.refrescar(peticion.refreshToken());
    }

    /**
     * Revoca el refresh token. El access token sigue siendo válido hasta que expire (entre
     * 30 y 120 minutos): es el compromiso habitual de los JWT autocontenidos, y por eso su
     * vida es corta.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest peticion) {
        servicio.logout(peticion == null ? null : peticion.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
