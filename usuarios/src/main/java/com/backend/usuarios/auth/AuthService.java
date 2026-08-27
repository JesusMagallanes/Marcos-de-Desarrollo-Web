package com.backend.usuarios.auth;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
import com.backend.usuarios.shared.validacion.Saneador;
import com.backend.usuarios.usuario.RolService;
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

    /*
     * Este mismo servicio, pero visto a traves del proxy de Spring.
     *
     * `cortarSesiones` escribe y justo despues se lanza la excepcion que
     * rechaza el refresco. Llamandolo directo, la escritura ocurria DENTRO de
     * la transaccion de `refrescar` y el rollback de esa excepcion se la
     * llevaba por delante: el corte no llegaba nunca a la base. Con el proxy si
     * se aplica su REQUIRES_NEW y el corte se confirma por su cuenta.
     *
     * Es un ObjectProvider y no una inyeccion normal porque una dependencia a
     * si mismo por constructor seria un ciclo.
     */
    private final ObjectProvider<AuthService> proxia;

    private final AuthenticationManager gestorAutenticacion;
    private final UsuarioRepository repositorio;
    private final RolService roles;
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
        metricas.loginCorrecto(usuario.getRol());

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
            /*
             * Reúso: o el token se filtró, o el cliente reintenta.
             *
             * Rechazar SOLO este token no basta, y esa era la parte que faltaba.
             * Si el refresh se filtró, el atacante ya lo canjeó y tiene un par
             * nuevo sin revocar; el que llega segundo —y se lleva el 401— es el
             * usuario legítimo, mientras la cadena robada sigue renovándose siete
             * días. Por eso se corta la cuenta entera: todo lo emitido hasta
             * ahora deja de valer, incluida esa cadena.
             *
             * Los tokens de ACCESO ya emitidos siguen vivos hasta que caducan
             * solos —los validan los otros tres servicios sin preguntar aquí—,
             * pero eso es como mucho una hora, frente a los siete días de
             * renovaciones que se cierran aquí.
             */
            proxia.getObject().cortarSesiones(claims.getSubject());

            auditoria.registrarFallo(Evento.LOGIN_FALLIDO, claims.getSubject(),
                    "reúso de refresh token revocado jti=" + claims.getId()
                            + "; se cortaron todas las sesiones de la cuenta");
            metricas.reusoRefreshDetectado();
            throw new BadCredentialsException("Token de refresco ya utilizado");
        }

        Usuario usuario = repositorio.findByEmailAddress(claims.getSubject())
                .orElseThrow(() -> new BadCredentialsException("Cuenta no encontrada"));

        if (emitidoAntesDelCorte(claims, usuario)) {
            // La cuenta se cortó por un reúso anterior. Este token es de antes,
            // así que ya no sirve aunque su firma y su caducidad estén bien.
            auditoria.registrarFallo(Evento.LOGIN_FALLIDO, claims.getSubject(),
                    "refresh anterior al corte de sesiones");
            throw new BadCredentialsException(
                    "Tu sesión se cerró por seguridad. Vuelve a iniciar sesión.");
        }

        tokenService.revocar(claims, TokenRevocado.Motivo.ROTACION);
        return construirRespuesta(usuario);
    }

    /**
     * Invalida todo lo emitido a esta cuenta hasta este instante.
     *
     * <p>No falla si la cuenta no existe: se llega aquí desde un token que ya
     * está verificado, pero el correo podría haberse dado de baja entretanto, y
     * lo que toca entonces es seguir adelante y rechazar el refresco igual.
     *
     * <p><b>REQUIRES_NEW no es decorativo, y por eso esto es público.</b> Quien
     * llama lanza un {@code BadCredentialsException} justo después, y una
     * excepción de runtime deshace la transacción de {@code refrescar}. Sin
     * transacción propia, este {@code save} se iba con ella: la mitigación
     * entera —cerrar la cuenta ante un reúso de refresh— no llegaba nunca a la
     * base de datos, y la cadena robada seguía renovándose siete días. Lo tapaba
     * que la prueba usa un repositorio simulado y sin transacción, donde el
     * {@code save} sí se ve.
     *
     * <p>Se invoca por {@code proxia} y no directamente: una llamada interna se
     * salta el proxy de Spring, y con él la anotación.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cortarSesiones(String email) {
        repositorio.findByEmailAddress(email).ifPresent(usuario -> {
            usuario.setTokensValidosDesde(LocalDateTime.now());
            repositorio.save(usuario);
            log.warn("Sesiones de {} cortadas por reúso de refresh token", email);
        });
    }

    /**
     * ¿Se emitió este token antes del corte?
     *
     * <p>`iat` viaja en segundos, así que la comparación es estricta: un token
     * emitido en el mismo segundo del corte se acepta. Eso es lo que se quiere
     * —el usuario que vuelve a entrar justo después no puede quedarse fuera por
     * un redondeo— y no abre nada: los tokens que se están invalidando son de
     * antes, no de ese mismo instante.
     */
    private boolean emitidoAntesDelCorte(Claims claims, Usuario usuario) {
        LocalDateTime corte = usuario.getTokensValidosDesde();
        if (corte == null || claims.getIssuedAt() == null) {
            return false;
        }
        LocalDateTime emitido = LocalDateTime.ofInstant(
                claims.getIssuedAt().toInstant(), ZoneId.systemDefault());
        return emitido.isBefore(corte.truncatedTo(ChronoUnit.SECONDS));
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
                .rol("CLIENTE")
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
                UsuarioResponse.desde(usuario, roles.permisosDe(usuario.getRol())));
    }

    private static String normalizar(String email) {
        return Saneador.normalizarEmail(email);
    }
}
