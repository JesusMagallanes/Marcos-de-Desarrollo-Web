package com.backend.usuarios.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.backend.usuarios.auth.token.TokenRevocado;
import com.backend.usuarios.auth.token.TokenService;
import com.backend.usuarios.shared.auditoria.AuditoriaService;
import com.backend.usuarios.shared.metricas.MetricasSeguridad;
import com.backend.usuarios.shared.security.JwtProperties;
import com.backend.usuarios.shared.seguridad.LimitadorPeticiones;
import com.backend.usuarios.usuario.RolService;
import com.backend.usuarios.usuario.Usuario;
import com.backend.usuarios.usuario.UsuarioRepository;

/**
 * Rotación de refresh tokens y qué pasa cuando uno se reutiliza.
 *
 * <p>El caso que da nombre a la clase es el que estaba a medias. Un refresh se
 * filtra; el atacante lo canjea y recibe un par nuevo; cuando el usuario
 * legítimo intenta canjear el suyo —ya revocado por la rotación— se detecta el
 * reúso. Hasta ahí, bien. Lo que faltaba es que <b>el que se quedaba fuera era
 * el legítimo</b>: la cadena del atacante no estaba revocada y le servía para
 * seguir renovando otros siete días.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Refresco de sesión")
class RefrescoDeSesionTest {

    private static final String SECRETO = "secreto-de-pruebas-suficientemente-largo-para-hs256";
    private static final String EMAIL = "ana@ejemplo.com";

    @Mock
    private ObjectProvider<AuthService> proxia;
    @Mock
    private AuthenticationManager gestorAutenticacion;
    @Mock
    private UsuarioRepository repositorio;
    @Mock
    private RolService roles;
    @Mock
    private PasswordEncoder codificador;
    @Mock
    private TokenService tokenService;
    @Mock
    private AuditoriaService auditoria;
    @Mock
    private MetricasSeguridad metricas;
    @Mock
    private LimitadorPeticiones limitador;

    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void preparar() {
        lenient().when(roles.permisosDe(anyString())).thenReturn(List.of());

        // JwtService de verdad: así los tokens del caso son tokens reales, con
        // su `iat` y su `jti`, que es justo lo que decide el resultado.
        jwtService = new JwtService(new JwtProperties(
                SECRETO, "smartzone-usuarios", "smartzone-api",
                3_600_000L, 7_200_000L, 1_800_000L, 604_800_000L), roles);

        authService = new AuthService(proxia, gestorAutenticacion, repositorio, roles, codificador,
                jwtService, tokenService, auditoria, metricas, limitador);

        // El "proxy" de estas pruebas es el propio servicio: aquí no hay Spring
        // ni transacciones, y lo que se comprueba es el flujo. Que la anotación
        // esté puesta lo vigila `cortarSesionesCorreEnSuPropiaTransaccion`.
        lenient().when(proxia.getObject()).thenAnswer(invocacion -> authService);
    }

    private Usuario ana() {
        return Usuario.builder().id(42L).emailAddress(EMAIL).rol("CLIENTE").build();
    }

    /* ══════════════ Camino normal ══════════════ */

    @Test
    @DisplayName("canjear un refresh válido devuelve un par nuevo y revoca el usado")
    void rotacion() {
        Usuario ana = ana();
        String refresh = jwtService.generarRefreshToken(ana);
        when(tokenService.estaRevocado(anyString())).thenReturn(false);
        when(repositorio.findByEmailAddress(EMAIL)).thenReturn(Optional.of(ana));

        var respuesta = authService.refrescar(refresh);

        assertThat(respuesta.accessToken()).isNotBlank();
        assertThat(respuesta.refreshToken()).isNotBlank().isNotEqualTo(refresh);

        // El usado se revoca en el acto: es lo que convierte un segundo uso en
        // una señal en vez de en una renovación más.
        verify(tokenService).revocar(any(), any());
    }

    @Test
    @DisplayName("un token de ACCESO no sirve para refrescar")
    void noSeAceptaUnAccessToken() {
        String acceso = jwtService.generarAccessToken(ana());

        assertThatThrownBy(() -> authService.refrescar(acceso))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("refresco");
    }

    @Test
    @DisplayName("un token ilegible se rechaza sin mirar nada más")
    void tokenBasura() {
        assertThatThrownBy(() -> authService.refrescar("esto-no-es-un-jwt"))
                .isInstanceOf(BadCredentialsException.class);

        verify(repositorio, never()).findByEmailAddress(anyString());
    }

    /* ══════════════ Reúso ══════════════ */

    @Test
    @DisplayName("reusar un refresh revocado corta TODAS las sesiones de la cuenta")
    void reusoCortaLaCuentaEntera() {
        Usuario ana = ana();
        String robado = jwtService.generarRefreshToken(ana);
        when(tokenService.estaRevocado(anyString())).thenReturn(true);
        when(repositorio.findByEmailAddress(EMAIL)).thenReturn(Optional.of(ana));

        assertThatThrownBy(() -> authService.refrescar(robado))
                .isInstanceOf(BadCredentialsException.class);

        /*
         * Esto es lo que faltaba. Sin el corte, el atacante conservaba el par
         * que ya había canjeado y seguía renovando durante siete días, mientras
         * el 401 se lo llevaba el usuario legítimo.
         */
        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(repositorio).save(guardado.capture());
        assertThat(guardado.getValue().getTokensValidosDesde()).isNotNull();

        verify(metricas).reusoRefreshDetectado();
        verify(auditoria).registrarFallo(any(), anyString(), anyString());

        // Y por el proxy, que es lo que hace que el corte sobreviva al rollback.
        verify(proxia).getObject();
    }

    @Test
    @DisplayName("el corte corre en su PROPIA transacción, o el rollback se lo lleva")
    void cortarSesionesCorreEnSuPropiaTransaccion() throws Exception {
        /*
         * Esta comprobación es estructural a propósito.
         *
         * El fallo que cubre no se ve con mocks: `refrescar` es @Transactional y
         * termina lanzando BadCredentialsException, que es de runtime y por
         * tanto deshace la transacción. El `save` del corte se iba con ella y la
         * cuenta NO quedaba cerrada, aunque aquí arriba el mock del repositorio
         * sí registre la llamada. Reproducirlo de verdad exige un contexto de
         * Spring con base de datos; vigilar la anotación cuesta cuatro líneas y
         * salta en el momento en que alguien la quite.
         */
        Transactional anotacion = AuthService.class
                .getMethod("cortarSesiones", String.class)
                .getAnnotation(Transactional.class);

        assertThat(anotacion)
                .as("cortarSesiones necesita @Transactional propio")
                .isNotNull();
        assertThat(anotacion.propagation())
                .as("sin REQUIRES_NEW el corte se deshace con la excepción que lo sigue")
                .isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    @DisplayName("tras el corte, un refresh anterior ya no vale aunque su firma sea buena")
    void despuesDelCorteNoValeLoViejo() {
        Usuario ana = ana();
        String anterior = jwtService.generarRefreshToken(ana);

        // El corte ocurre DESPUÉS de emitirse ese token.
        ana.setTokensValidosDesde(LocalDateTime.now().plusSeconds(5));
        when(tokenService.estaRevocado(anyString())).thenReturn(false);
        when(repositorio.findByEmailAddress(EMAIL)).thenReturn(Optional.of(ana));

        assertThatThrownBy(() -> authService.refrescar(anterior))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Vuelve a iniciar sesión");

        // Y no se rota: no se le entrega un par nuevo a quien traía uno cortado.
        verify(tokenService, never()).revocar(any(), any());
    }

    @Test
    @DisplayName("tras el corte, volver a entrar funciona: lo nuevo sí vale")
    void despuesDelCorteLoNuevoSiVale() {
        Usuario ana = ana();
        // Corte en el pasado; el token se emite ahora, después.
        ana.setTokensValidosDesde(LocalDateTime.now().minusMinutes(10));
        String reciente = jwtService.generarRefreshToken(ana);

        when(tokenService.estaRevocado(anyString())).thenReturn(false);
        when(repositorio.findByEmailAddress(EMAIL)).thenReturn(Optional.of(ana));

        var respuesta = authService.refrescar(reciente);

        assertThat(respuesta.accessToken()).isNotBlank();
        verify(tokenService).revocar(any(), any());
    }

    @Test
    @DisplayName("una cuenta sin corte se comporta como siempre")
    void sinCorteTodoIgual() {
        Usuario ana = ana();
        assertThat(ana.getTokensValidosDesde()).isNull();

        when(tokenService.estaRevocado(anyString())).thenReturn(false);
        when(repositorio.findByEmailAddress(EMAIL)).thenReturn(Optional.of(ana));

        assertThat(authService.refrescar(jwtService.generarRefreshToken(ana)).accessToken())
                .isNotBlank();
    }

    /* ══════════════ Logout ══════════════ */

    @Test
    @DisplayName("el logout revoca el refresh con su motivo")
    void logout() {
        authService.logout(jwtService.generarRefreshToken(ana()));

        ArgumentCaptor<TokenRevocado.Motivo> motivo =
                ArgumentCaptor.forClass(TokenRevocado.Motivo.class);
        verify(tokenService).revocar(any(), motivo.capture());
        assertThat(motivo.getValue()).isEqualTo(TokenRevocado.Motivo.LOGOUT);
    }

    @Test
    @DisplayName("un logout con un token ilegible no revienta: ya no hay nada que revocar")
    void logoutConBasura() {
        authService.logout("no-es-un-jwt");
        authService.logout(null);

        verify(tokenService, never()).revocar(any(), any());
    }
}
