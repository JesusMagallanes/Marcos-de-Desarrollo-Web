package com.backend.catalogo.shared.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

@DisplayName("JwtUtils - uidDe")
class JwtUtilsTest {

    @Test
    @DisplayName("extrae el uid numérico del JWT")
    void extraeUid() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("uid")).thenReturn(42);

        assertThat(JwtUtils.uidDe(jwt)).isEqualTo(42L);
    }

    @Test
    @DisplayName("devuelve null si el JWT es null")
    void jwtNull() {
        assertThat(JwtUtils.uidDe(null)).isNull();
    }

    @Test
    @DisplayName("devuelve null si el claim uid no existe")
    void sinClaimUid() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("uid")).thenReturn(null);

        assertThat(JwtUtils.uidDe(jwt)).isNull();
    }

    @Test
    @DisplayName("convierte Integer a Long")
    void integerALong() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("uid")).thenReturn(100);

        assertThat(JwtUtils.uidDe(jwt)).isEqualTo(100L);
    }
}
