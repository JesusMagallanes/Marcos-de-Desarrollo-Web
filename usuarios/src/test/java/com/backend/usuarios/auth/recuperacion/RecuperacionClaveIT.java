package com.backend.usuarios.auth.recuperacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.backend.usuarios.PruebaIntegracion;
import com.backend.usuarios.auth.AuthController;
import com.backend.usuarios.auth.dto.AuthDtos.RecuperarRequest;
import com.backend.usuarios.auth.dto.AuthDtos.RestablecerRequest;
import com.backend.usuarios.shared.correo.CorreoService;
import com.backend.usuarios.shared.error.DatosInvalidosException;
import com.backend.usuarios.shared.seguridad.ContextoRls;
import com.backend.usuarios.usuario.Usuario;
import com.backend.usuarios.usuario.UsuarioRepository;

/**
 * El flujo entero contra Postgres de verdad, con Row Level Security activo.
 *
 * <p>Lo que las unitarias no pueden ver: que cada consulta corre como sistema
 * (si no, RLS la dejaría sin filas y el servicio diría "sin cuenta" en
 * silencio), y que el intento fallido QUEDA GUARDADO aunque la respuesta sea un
 * error — si la excepción deshiciera la transacción, el tope de intentos no
 * existiría.
 */
@EnabledIf(
        value = "com.backend.usuarios.Docker#disponible",
        disabledReason = "Docker no está disponible: se omiten las pruebas de integración")
@DisplayName("Recuperar la contraseña, de punta a punta")
class RecuperacionClaveIT extends PruebaIntegracion {

    private static final String CORREO = "recupera.integracion@smartzone.test";
    private static final String CLAVE_NUEVA = "Nueva#2025x";

    @Autowired
    private AuthController controlador;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private PasswordEncoder codificador;
    @Autowired
    private JdbcTemplate jdbc;

    /** El correo no sale: se captura el código que se habría enviado. */
    @MockitoBean
    private CorreoService correo;

    @BeforeEach
    void preparar() {
        ContextoRls.comoSistema(() -> {
            usuarios.findByEmailAddress(CORREO).ifPresent(usuarios::delete);
            usuarios.save(Usuario.builder()
                    .name("Rosa")
                    .lastname("Huamán")
                    .emailAddress(CORREO)
                    .password(codificador.encode("Vieja#2024"))
                    .build());
        });
    }

    private Usuario rosa() {
        return ContextoRls.comoSistema(() -> usuarios.findByEmailAddress(CORREO).orElseThrow());
    }

    private int intentosDelUltimoCodigo() {
        return ContextoRls.comoSistema(() -> jdbc.queryForObject("""
                SELECT c.intentos FROM usuarios.codigo_recuperacion c
                JOIN usuarios.usuario u ON u.id = c.usuario_id
                WHERE u.email_address = ? ORDER BY c.creado_en DESC, c.id DESC LIMIT 1
                """, Integer.class, CORREO));
    }

    private String pedirCodigo() {
        var respuesta = controlador.recuperar(new RecuperarRequest(CORREO));
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
        verify(correo, timeout(3000)).codigoRecuperacion(eq(CORREO), eq("Rosa"), codigo.capture(), anyLong());
        return codigo.getValue();
    }

    @Test
    @DisplayName("pedir código, fallar uno, acertar: cambia la clave, corta sesiones y el código no se reutiliza")
    void flujoCompleto() {
        String codigo = pedirCodigo();
        String equivocado = codigo.equals("000000") ? "111111" : "000000";

        assertThatThrownBy(() -> controlador.restablecer(new RestablecerRequest(CORREO, equivocado, CLAVE_NUEVA)))
                .isInstanceOf(DatosInvalidosException.class);
        assertThat(intentosDelUltimoCodigo())
                .as("el intento fallido sobrevive al 400")
                .isEqualTo(1);

        var respuesta = controlador.restablecer(new RestablecerRequest(CORREO, codigo, CLAVE_NUEVA));
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Usuario despues = rosa();
        assertThat(codificador.matches(CLAVE_NUEVA, despues.getPassword())).isTrue();
        assertThat(despues.getTokensValidosDesde()).as("sesiones cortadas").isNotNull();

        assertThatThrownBy(() -> controlador.restablecer(new RestablecerRequest(CORREO, codigo, "Otra#2026xx")))
                .as("el mismo código no sirve dos veces")
                .isInstanceOf(DatosInvalidosException.class);
        assertThat(codificador.matches(CLAVE_NUEVA, rosa().getPassword())).isTrue();
    }

    @Test
    @DisplayName("un correo sin cuenta recibe el mismo 202 y no se envía nada")
    void correoSinCuenta() {
        var respuesta = controlador.recuperar(new RecuperarRequest("nadie.aqui@smartzone.test"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(correo, after(500).never()).codigoRecuperacion(anyString(), anyString(), anyString(), anyLong());
    }
}
