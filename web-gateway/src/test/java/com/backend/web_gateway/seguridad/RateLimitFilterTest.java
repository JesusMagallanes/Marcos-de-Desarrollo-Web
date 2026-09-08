package com.backend.web_gateway.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.backend.web_gateway.metricas.MetricasSeguridad;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * El reparto de cupos, que es donde se decide a quién se deja fuera.
 *
 * <p>La regla que se fija aquí: el cupo de autenticación protege de la
 * adivinación de credenciales, y adivinar es siempre una petición que cambia
 * estado. Las lecturas públicas que cuelgan de la misma ruta no pueden gastarlo.
 */
class RateLimitFilterTest {

    private RateLimitFilter filtro;

    @BeforeEach
    void preparar() {
        LimitadorPeticiones limitador = new LimitadorPeticiones();
        filtro = new RateLimitFilter(
                limitador,
                new MetricasSeguridad(new SimpleMeterRegistry(), limitador),
                new IpCliente(List.of("127.0.0.1")));
    }

    /** @return el estado de la última respuesta tras repetir la petición n veces. */
    private int repetir(String metodo, String ruta, int veces) throws Exception {
        int estado = 200;
        for (int i = 0; i < veces; i++) {
            MockHttpServletRequest peticion = new MockHttpServletRequest(metodo, ruta);
            peticion.setRemoteAddr("203.0.113.9");
            MockHttpServletResponse respuesta = new MockHttpServletResponse();
            filtro.doFilter(peticion, respuesta, new MockFilterChain());
            estado = respuesta.getStatus();
        }
        return estado;
    }

    @Test
    @DisplayName("los intentos de acceso siguen acotados: el 21º se rechaza")
    void elLoginSigueLimitado() throws Exception {
        assertThat(repetir("POST", "/api/auth/login", 21)).isEqualTo(429);
    }

    @Test
    @DisplayName("navegar no gasta intentos de acceso")
    void lasLecturasDeAuthNoGastanElCupoDeLogin() throws Exception {
        /*
         * `/yo` la pide el arranque de la aplicación en CADA carga de página y
         * `/proveedores` cada vez que se pinta el formulario de acceso. Cuando
         * gastaban el cupo de autenticación —20 cada 15 minutos por IP—, en un
         * locutorio o una oficina detrás de una sola salida a internet la gente
         * se quedaba sin poder entrar por haber navegado, sin que nadie hubiera
         * fallado una contraseña.
         */
        assertThat(repetir("GET", "/api/auth/yo", 60)).isEqualTo(200);
        assertThat(repetir("GET", "/api/auth/proveedores", 60)).isEqualTo(200);
    }

    @Test
    @DisplayName("el baile de OAuth sí cuenta como autenticación, aunque sea GET")
    void oauthCuentaComoAutenticacion() throws Exception {
        assertThat(repetir("GET", "/oauth2/authorization/google", 21)).isEqualTo(429);
    }
}
