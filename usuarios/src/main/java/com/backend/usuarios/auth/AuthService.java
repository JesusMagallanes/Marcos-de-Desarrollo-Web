package com.backend.usuarios.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.usuarios.auth.dto.AuthDtos.AuthResponse;
import com.backend.usuarios.auth.dto.AuthDtos.LoginRequest;
import com.backend.usuarios.auth.dto.AuthDtos.RegistroRequest;
import com.backend.usuarios.auth.token.TokenRevocado;
import com.backend.usuarios.auth.token.TokenService;
import com.backend.usuarios.shared.auditoria.AuditoriaService;
import com.backend.usuarios.shared.metricas.MetricasSeguridad;
import com.backend.usuarios.shared.auditoria.AuditoriaService.Evento;
import com.backend.usuarios.shared.error.ConflictoException;
import com.backend.usuarios.shared.seguridad.LimitadorPeticiones;
import com.backend.usuarios.usuario.Rol;
import com.backend.usuarios.usuario.Usuario;
import com.backend.usuarios.usuario.UsuarioRepository;
import com.backend.usuarios.usuario.dto.UsuarioDtos.UsuarioResponse;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager gestorAutenticacion;
    private final UsuarioRepository repositorio;
    private final PasswordEncoder codificador;
    private final JwtService jwtService;
    private final TokenService tokenService;
    private final AuditoriaService auditoria;
    private final MetricasSeguridad metricas;
    private final LimitadorPeticiones limitador;

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest peticion) {
        String email = normalizar(peticion.email());

        try {
            // Lanza BadCredentialsException, que el handler traduce a 401 con
            // mensaje genérico: no revelamos si el correo existe.
            gestorAutenticacion.authenticate(
                    new UsernamePasswordAuthenticationToken(email, peticion.password()));

        } catch (BadCredentialsException ex) {
            auditoria.registrarFallo(Evento.LOGIN_FALLIDO, email, "credenciales inválidas");
            metricas.loginFallido("credenciales");
            throw ex;
        }

        Usuario usuario = repositorio.findByEmailAddress(email).orElseThrow();
        auditoria.registrar(Evento.LOGIN_OK, email, "rol=" + usuario.getRol());
        metricas.loginCorrecto(usuario.getRol().name());

        // Login correcto: se libera el cupo de intentos de esa IP+correo.
        limitador.limpiar("login|" + email);

        return construirRespuesta(usuario);
    }

    /**
     * Rota el par de tokens. El refresh usado se revoca en el acto, así que si
     * alguien lo reutiliza (porque lo robó, o porque el legítimo ya lo canjeó)
     * el segundo intento falla y queda registrado.
     */
    @Transactional
    public AuthResponse refrescar(String refreshToken) {
        Claims claims;
        try {
            claims = jwtService.verificar(refreshToken);
        } catch (JwtException ex) {
            throw new BadCredentialsException("Token de refresco inválido");
        }

        if (!JwtService.TIPO_REFRESH.equals(claims.get(JwtService.CLAIM_TIPO, String.class))) {
            throw new BadCredentialsException("Se esperaba un token de refresco");
        }

        if (tokenService.estaRevocado(claims.getId())) {
            // Reúso: o el token se filtró, o el cliente reintenta. En ambos
            // casos se rechaza y se deja constancia.
            auditoria.registrarFallo(Evento.LOGIN_FALLIDO, claims.getSubject(),
                    "reúso de refresh token revocado jti=" + claims.getId());
            metricas.reusoRefreshDetectado();
            throw new BadCredentialsException("Token de refresco ya utilizado");
        }

        Usuario usuario = repositorio.findByEmailAddress(claims.getSubject())
                .orElseThrow(() -> new BadCredentialsException("Cuenta no encontrada"));

        tokenService.revocar(claims, TokenRevocado.Motivo.ROTACION);
        return construirRespuesta(usuario);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        try {
            Claims claims = jwtService.verificar(refreshToken);
            tokenService.revocar(claims, TokenRevocado.Motivo.LOGOUT);
        } catch (JwtException ex) {
            // Un token ilegible ya no sirve para nada; no hay nada que revocar.
            log.debug("Logout con token no verificable: {}", ex.getMessage());
        }
    }

    @Transactional
    public AuthResponse registrar(RegistroRequest peticion) {
        String email = normalizar(peticion.emailAddress());
        if (repositorio.existsByEmailAddress(email)) {
            throw new ConflictoException("Ya existe una cuenta con ese correo");
        }

        Usuario usuario = Usuario.builder()
                .name(peticion.name())
                .lastname(peticion.lastname())
                .emailAddress(email)
                .password(codificador.encode(peticion.password()))
                .phoneNumber(peticion.phoneNumber())
                .address(peticion.address())
                .rol(Rol.CLIENTE)
                .build();

        Usuario guardado = repositorio.save(usuario);
        auditoria.registrar(Evento.REGISTRO, email, "alta local");
        metricas.registroCorrecto();
        return construirRespuesta(guardado);
    }

    private AuthResponse construirRespuesta(Usuario usuario) {
        metricas.tokenEmitido();
        return new AuthResponse(
                jwtService.generarAccessToken(usuario),
                jwtService.generarRefreshToken(usuario),
                usuario.getRol(),
                jwtService.duracionSegundos(usuario.getRol()),
                UsuarioResponse.desde(usuario));
    }

    private static String normalizar(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
