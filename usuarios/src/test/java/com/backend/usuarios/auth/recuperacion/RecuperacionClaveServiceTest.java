package com.backend.usuarios.auth.recuperacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.backend.usuarios.shared.auditoria.AuditoriaService;
import com.backend.usuarios.shared.correo.CorreoService;
import com.backend.usuarios.shared.seguridad.LimitadorPeticiones;
import com.backend.usuarios.usuario.Proveedor;
import com.backend.usuarios.usuario.Usuario;
import com.backend.usuarios.usuario.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Recuperar la contraseña")
class RecuperacionClaveServiceTest {

    private static final String EMAIL = "ana@ejemplo.com";
    private static final String CLAVE_VIEJA = "Vieja#2024";
    private static final String CLAVE_NUEVA = "Nueva#2025x";

    /** BCrypt de verdad, con coste mínimo para que la prueba no tarde. */
    private final PasswordEncoder codificador = new BCryptPasswordEncoder(4);

    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private CodigoRecuperacionRepository codigos;
    @Mock
    private CorreoService correo;
    @Mock
    private AuditoriaService auditoria;
    @Mock
    private LimitadorPeticiones limitador;

    private RecuperacionClaveService servicio;
    private Usuario ana;

    @BeforeEach
    void preparar() {
        servicio = new RecuperacionClaveService(usuarios, codigos, codificador, correo, auditoria, limitador);
        ana = Usuario.builder()
                .id(7L)
                .name("Ana")
                .lastname("Pérez")
                .emailAddress(EMAIL)
                .password(codificador.encode(CLAVE_VIEJA))
                .proveedor(Proveedor.LOCAL)
                .build();
        lenient().when(limitador.permitir(anyString(), any(Integer.class), any())).thenReturn(true);
        lenient().when(usuarios.findByEmailAddress(EMAIL)).thenReturn(Optional.of(ana));
    }

    private CodigoRecuperacion codigo(String enClaro) {
        return CodigoRecuperacion.builder()
                .id(1L)
                .usuarioId(ana.getId())
                .codigoHash(codificador.encode(enClaro))
                .expiraEn(Instant.now().plus(RecuperacionClaveService.VIGENCIA))
                .creadoEn(Instant.now())
                .build();
    }

    /* ── Pedir el código ── */

    @Test
    @DisplayName("emite un código de 6 dígitos, guarda solo su hash y lo envía")
    void emiteYEnvia() {
        when(codigos.findByUsuarioIdAndUsadoEnIsNull(7L)).thenReturn(List.of());

        servicio.solicitar(EMAIL);

        ArgumentCaptor<CodigoRecuperacion> guardado = ArgumentCaptor.forClass(CodigoRecuperacion.class);
        verify(codigos).save(guardado.capture());
        ArgumentCaptor<String> enviado = ArgumentCaptor.forClass(String.class);
        verify(correo, timeout(2000)).codigoRecuperacion(eq(EMAIL), eq("Ana"), enviado.capture(), anyLong());

        assertThat(enviado.getValue()).matches("\\d{6}");
        assertThat(guardado.getValue().getCodigoHash())
                .isNotEqualTo(enviado.getValue())
                .satisfies(hash -> assertThat(codificador.matches(enviado.getValue(), hash)).isTrue());
        assertThat(guardado.getValue().getExpiraEn()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("pedir otro código anula los anteriores: solo vale el último")
    void anulaLosAnteriores() {
        CodigoRecuperacion anterior = codigo("111111");
        when(codigos.findByUsuarioIdAndUsadoEnIsNull(7L)).thenReturn(List.of(anterior));

        servicio.solicitar(EMAIL);

        assertThat(anterior.getUsadoEn()).isNotNull();
    }

    @Test
    @DisplayName("un correo sin cuenta no recibe nada y no se distingue por fuera")
    void sinCuenta() {
        when(usuarios.findByEmailAddress("nadie@ejemplo.com")).thenReturn(Optional.empty());

        servicio.solicitar("nadie@ejemplo.com");

        verify(codigos, never()).save(any());
        verify(correo, after(300).never()).codigoRecuperacion(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("una cuenta de Google recibe un aviso, no un código")
    void cuentaDeGoogle() {
        ana.setProveedor(Proveedor.GOOGLE);
        ana.setPassword(null);

        servicio.solicitar(EMAIL);

        verify(codigos, never()).save(any());
        verify(correo, timeout(2000)).recuperacionConProveedor(EMAIL, "GOOGLE");
    }

    @Test
    @DisplayName("agotado el cupo del correo, no se emite ni se envía nada")
    void cupoPorCorreo() {
        when(limitador.permitir(eq("recuperar|" + EMAIL), any(Integer.class), any())).thenReturn(false);

        servicio.solicitar(EMAIL);

        verify(codigos, never()).save(any());
        verify(correo, after(300).never()).codigoRecuperacion(anyString(), anyString(), anyString(), anyLong());
    }

    /* ── Cambiar la contraseña ── */

    @Test
    @DisplayName("con el código correcto cambia la clave, gasta el código y corta las sesiones")
    void restableceYCortaSesiones() {
        CodigoRecuperacion vigente = codigo("123456");
        when(codigos.findFirstByUsuarioIdOrderByCreadoEnDescIdDesc(7L)).thenReturn(Optional.of(vigente));

        boolean cambiada = servicio.restablecer(EMAIL, "123456", CLAVE_NUEVA);

        assertThat(cambiada).isTrue();
        assertThat(codificador.matches(CLAVE_NUEVA, ana.getPassword())).isTrue();
        assertThat(vigente.getUsadoEn()).isNotNull();
        assertThat(ana.getTokensValidosDesde()).isNotNull();
        verify(limitador).limpiar("login|" + EMAIL);
    }

    @Test
    @DisplayName("un código equivocado cuenta el intento y no toca la clave")
    void codigoEquivocado() {
        CodigoRecuperacion vigente = codigo("123456");
        when(codigos.findFirstByUsuarioIdOrderByCreadoEnDescIdDesc(7L)).thenReturn(Optional.of(vigente));

        boolean cambiada = servicio.restablecer(EMAIL, "654321", CLAVE_NUEVA);

        assertThat(cambiada).isFalse();
        assertThat(vigente.getIntentos()).isEqualTo(1);
        assertThat(codificador.matches(CLAVE_VIEJA, ana.getPassword())).isTrue();
        assertThat(ana.getTokensValidosDesde()).isNull();
    }

    @Test
    @DisplayName("agotados los intentos, ni el código correcto sirve")
    void intentosAgotados() {
        CodigoRecuperacion vigente = codigo("123456");
        vigente.setIntentos(RecuperacionClaveService.MAXIMO_INTENTOS);
        when(codigos.findFirstByUsuarioIdOrderByCreadoEnDescIdDesc(7L)).thenReturn(Optional.of(vigente));

        assertThat(servicio.restablecer(EMAIL, "123456", CLAVE_NUEVA)).isFalse();
        assertThat(codificador.matches(CLAVE_VIEJA, ana.getPassword())).isTrue();
    }

    @Test
    @DisplayName("un código caducado no sirve")
    void caducado() {
        CodigoRecuperacion vigente = codigo("123456");
        vigente.setExpiraEn(Instant.now().minusSeconds(1));
        when(codigos.findFirstByUsuarioIdOrderByCreadoEnDescIdDesc(7L)).thenReturn(Optional.of(vigente));

        assertThat(servicio.restablecer(EMAIL, "123456", CLAVE_NUEVA)).isFalse();
    }

    @Test
    @DisplayName("un código ya usado no sirve dos veces")
    void yaUsado() {
        CodigoRecuperacion vigente = codigo("123456");
        vigente.setUsadoEn(Instant.now());
        when(codigos.findFirstByUsuarioIdOrderByCreadoEnDescIdDesc(7L)).thenReturn(Optional.of(vigente));

        assertThat(servicio.restablecer(EMAIL, "123456", CLAVE_NUEVA)).isFalse();
    }

    @Test
    @DisplayName("sin cuenta responde igual que con código equivocado")
    void restablecerSinCuenta() {
        when(usuarios.findByEmailAddress("nadie@ejemplo.com")).thenReturn(Optional.empty());

        assertThat(servicio.restablecer("nadie@ejemplo.com", "123456", CLAVE_NUEVA)).isFalse();
    }
}
