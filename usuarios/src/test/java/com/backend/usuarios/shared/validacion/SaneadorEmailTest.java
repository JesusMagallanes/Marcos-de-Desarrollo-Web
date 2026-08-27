package com.backend.usuarios.shared.validacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Saneador - normalizarEmail")
class SaneadorEmailTest {

    @Test
    @DisplayName("convierte a minúsculas y recorta espacios")
    void normaliza() {
        assertThat(Saneador.normalizarEmail("  Admin@SmartZone.COM  "))
                .isEqualTo("admin@smartzone.com");
    }

    @Test
    @DisplayName("devuelve null si recibe null")
    void nullSafe() {
        assertThat(Saneador.normalizarEmail(null)).isNull();
    }

    @Test
    @DisplayName("un email ya normalizado se mantiene igual")
    void yaNormalizado() {
        assertThat(Saneador.normalizarEmail("test@test.com"))
                .isEqualTo("test@test.com");
    }
}
