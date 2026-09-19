package com.backend.catalogo.shared.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * La puerta de las métricas: un recolector con el secreto, o una persona
 * administradora. Nadie más, y sin secreto configurado nada cambia.
 */
@DisplayName("Acceso a /actuator/prometheus")
class AccesoMetricasTest {

    private static final Supplier<Authentication> ANONIMO = () -> new AnonymousAuthenticationToken(
            "clave", "anonimo", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

    private static final Supplier<Authentication> ADMIN = () -> new UsernamePasswordAuthenticationToken(
            "admin", "x", List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")));

    private static RequestAuthorizationContext con(String tokenRecibido) {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/actuator/prometheus");
        if (tokenRecibido != null) {
            peticion.addHeader(AccesoMetricas.CABECERA, tokenRecibido);
        }
        return new RequestAuthorizationContext(peticion);
    }

    private static boolean permite(String configurado, String recibido, Supplier<Authentication> quien) {
        var resultado = AccesoMetricas.conTokenOAdministrador(configurado).authorize(quien, con(recibido));
        return resultado != null && resultado.isGranted();
    }

    @Test
    @DisplayName("el recolector entra con el secreto, sin sesión")
    void conElSecreto() {
        assertThat(permite("secreto-del-scrape", "secreto-del-scrape", ANONIMO)).isTrue();
    }

    @Test
    @DisplayName("un secreto equivocado no abre nada")
    void secretoEquivocado() {
        assertThat(permite("secreto-del-scrape", "otro", ANONIMO)).isFalse();
        assertThat(permite("secreto-del-scrape", null, ANONIMO)).isFalse();
    }

    @Test
    @DisplayName("sin secreto configurado, la cabecera no sirve de nada")
    void sinSecretoConfigurado() {
        assertThat(permite("", "", ANONIMO)).isFalse();
        assertThat(permite("  ", "  ", ANONIMO)).isFalse();
        assertThat(permite(null, "lo-que-sea", ANONIMO)).isFalse();
    }

    @Test
    @DisplayName("el administrador sigue entrando con su JWT, haya secreto o no")
    void administrador() {
        assertThat(permite("secreto-del-scrape", null, ADMIN)).isTrue();
        assertThat(permite("", null, ADMIN)).isTrue();
    }
}
