package com.backend.catalogo.shared.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

/**
 * CORS es de esos controles que, mal puestos, no fallan al arrancar: fallan en
 * producción y solo en las escrituras. Estas pruebas fijan las dos mitades del
 * comportamiento para que no se pierda una arreglando la otra.
 */
class CorsConfigTest {

    private static final List<String> LISTA = List.of("https://smartzone.pe", "http://localhost:4200");
    private static final IpCliente PROXIES = new IpCliente(List.of("127.0.0.1", "10."));

    private CorsConfiguration resolver(MockHttpServletRequest peticion) {
        return new CorsConfig(LISTA, PROXIES).corsConfigurationSource().getCorsConfiguration(peticion);
    }

    private MockHttpServletRequest peticion(String host, String origen) {
        MockHttpServletRequest p = new MockHttpServletRequest("POST", "/api/auth/login");
        p.addHeader("Host", host);
        if (origen != null) {
            p.addHeader("Origin", origen);
        }
        return p;
    }

    @Nested
    @DisplayName("mismo origen")
    class MismoOrigen {

        /**
         * El caso que se escapó: el navegador manda `Origin` también en las
         * peticiones del propio sitio cuando no son GET. Si el dominio de
         * despliegue no está en la lista blanca, login, carrito y checkout
         * empiezan a devolver 403 mientras la navegación (GET) sigue bien.
         */
        @Test
        @DisplayName("se permite aunque el dominio NO esté en la lista blanca")
        void dominioNoListadoPeroMismoOrigen() {
            CorsConfiguration config = resolver(
                    peticion("tienda-nueva.com", "http://tienda-nueva.com"));

            assertThat(config.getAllowedOrigins()).containsExactly("http://tienda-nueva.com");
        }

        @Test
        @DisplayName("respeta X-Forwarded-Proto: tras el proxy el esquema real es https")
        void detrasDeProxyConTls() {
            MockHttpServletRequest p = peticion("smartzone.pe", "https://smartzone.pe");
            p.addHeader("X-Forwarded-Proto", "https");

            assertThat(resolver(p).getAllowedOrigins()).containsExactly("https://smartzone.pe");
        }

        @Test
        @DisplayName("el puerto forma parte del origen: distinto puerto no es el mismo origen")
        void puertoDistintoNoEsMismoOrigen() {
            CorsConfiguration config = resolver(
                    peticion("tienda-nueva.com", "http://tienda-nueva.com:8443"));

            assertThat(config.getAllowedOrigins()).isEqualTo(LISTA);
        }

        /**
         * El segundo salto: el gateway reescribe Host con el destino interno, así
         * que el servicio de dentro solo puede reconocer el origen real mirando
         * X-Forwarded-Host. Sin esto, todas las escrituras morían con 403 dentro
         * de la red aunque el gateway las hubiera dado por buenas.
         */
        @Test
        @DisplayName("tras el gateway se reconoce por X-Forwarded-Host")
        void detrasDelGateway() {
            MockHttpServletRequest p = peticion("localhost:8082", "https://smartzone-nueva.com");
            p.setRemoteAddr("127.0.0.1");
            p.addHeader("X-Forwarded-Host", "smartzone-nueva.com");
            p.addHeader("X-Forwarded-Proto", "https");

            assertThat(resolver(p).getAllowedOrigins()).containsExactly("https://smartzone-nueva.com");
        }

        @Test
        @DisplayName("X-Forwarded-Host de un emisor NO fiable se ignora")
        void cabeceraReenviadaFalsificada() {
            MockHttpServletRequest p = peticion("localhost:8082", "https://evil.com");
            p.setRemoteAddr("203.0.113.9");
            p.addHeader("X-Forwarded-Host", "evil.com");
            p.addHeader("X-Forwarded-Proto", "https");

            assertThat(resolver(p).getAllowedOrigins())
                    .isEqualTo(LISTA)
                    .doesNotContain("https://evil.com");
        }
    }

    @Nested
    @DisplayName("origen ajeno")
    class OrigenAjeno {

        @Test
        @DisplayName("una web cualquiera solo obtiene la lista blanca, que no la incluye")
        void webAjena() {
            CorsConfiguration config = resolver(
                    peticion("smartzone.pe", "https://evil.com"));

            assertThat(config.getAllowedOrigins())
                    .isEqualTo(LISTA)
                    .doesNotContain("https://evil.com");
        }

        @Test
        @DisplayName("un origen de la lista blanca sí se admite")
        void origenListado() {
            CorsConfiguration config = resolver(
                    peticion("smartzone.pe", "http://localhost:4200"));

            assertThat(config.getAllowedOrigins()).contains("http://localhost:4200");
        }
    }

    @Nested
    @DisplayName("política")
    class Politica {

        @Test
        @DisplayName("nunca se admiten credenciales ni cabeceras con comodín")
        void sinCredencialesNiComodines() {
            CorsConfiguration config = resolver(peticion("smartzone.pe", "https://smartzone.pe"));

            assertThat(config.getAllowCredentials()).isNotEqualTo(Boolean.TRUE);
            assertThat(config.getAllowedHeaders()).doesNotContain("*");
            assertThat(config.getAllowedOrigins()).doesNotContain("*");
        }

        @ParameterizedTest
        @ValueSource(strings = { "*", "https://*.smartzone.pe", "http://localhost:*" })
        @DisplayName("un comodín en la configuración impide arrancar")
        void comodinRechazado(String origen) {
            assertThatThrownBy(() -> new CorsConfig(List.of(origen), PROXIES))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("comodines");
        }

        @Test
        @DisplayName("una lista vacía impide arrancar en vez de abrir la API")
        void listaVaciaRechazada() {
            assertThatThrownBy(() -> new CorsConfig(List.of("  "), PROXIES))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
