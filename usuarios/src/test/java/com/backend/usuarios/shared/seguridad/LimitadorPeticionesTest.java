package com.backend.usuarios.shared.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A07: es la barrera contra fuerza bruta en el login. */
class LimitadorPeticionesTest {

    @Test
    @DisplayName("permite hasta el cupo y bloquea a partir de ahí")
    void cupo() {
        LimitadorPeticiones limitador = new LimitadorPeticiones();
        Duration ventana = Duration.ofMinutes(5);

        for (int i = 1; i <= 3; i++) {
            assertThat(limitador.permitir("ip|login", 3, ventana))
                    .as("intento %d", i).isTrue();
        }

        assertThat(limitador.permitir("ip|login", 3, ventana)).isFalse();
        assertThat(limitador.permitir("ip|login", 3, ventana)).isFalse();
    }

    @Test
    @DisplayName("cada clave lleva su propia cuenta")
    void clavesIndependientes() {
        LimitadorPeticiones limitador = new LimitadorPeticiones();
        Duration ventana = Duration.ofMinutes(5);

        assertThat(limitador.permitir("ip-a|login", 1, ventana)).isTrue();
        assertThat(limitador.permitir("ip-a|login", 1, ventana)).isFalse();

        // Otra IP no debe verse afectada por el bloqueo de la primera.
        assertThat(limitador.permitir("ip-b|login", 1, ventana)).isTrue();
    }

    @Test
    @DisplayName("al expirar la ventana el cupo se renueva")
    void ventanaDeslizante() throws InterruptedException {
        LimitadorPeticiones limitador = new LimitadorPeticiones();
        Duration ventana = Duration.ofMillis(100);

        assertThat(limitador.permitir("ip|login", 1, ventana)).isTrue();
        assertThat(limitador.permitir("ip|login", 1, ventana)).isFalse();

        Thread.sleep(150);

        assertThat(limitador.permitir("ip|login", 1, ventana)).isTrue();
    }

    @Test
    @DisplayName("un login correcto libera el cupo acumulado")
    void limpiezaTrasExito() {
        LimitadorPeticiones limitador = new LimitadorPeticiones();
        Duration ventana = Duration.ofMinutes(5);

        limitador.permitir("ip|login", 1, ventana);
        assertThat(limitador.permitir("ip|login", 1, ventana)).isFalse();

        limitador.limpiar("ip|login");

        assertThat(limitador.permitir("ip|login", 1, ventana)).isTrue();
    }

    @Test
    @DisplayName("informa cuántos segundos faltan para reintentar")
    void tiempoDeEspera() {
        LimitadorPeticiones limitador = new LimitadorPeticiones();
        Duration ventana = Duration.ofSeconds(30);

        limitador.permitir("ip|login", 1, ventana);

        long espera = limitador.segundosParaReintentar("ip|login", ventana);
        assertThat(espera).isBetween(1L, 30L);
    }
}
