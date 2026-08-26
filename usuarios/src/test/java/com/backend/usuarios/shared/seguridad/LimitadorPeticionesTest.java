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
    @DisplayName("no crece sin freno: pasado el tope de claves, deja de guardar nuevas")
    void topeDeClaves() {
        LimitadorPeticiones limitador = new LimitadorPeticiones();
        Duration ventana = Duration.ofMinutes(5);

        /*
         * El escenario real: alguien machaca el login con un correo distinto
         * cada vez. Cada intento es una clave nueva, y las claves las elige él.
         *
         * La purga descarta lo que lleve media hora y corre cada cinco minutos,
         * así que no frena esto: entre pasada y pasada el mapa crecía sin
         * límite, en el servicio que emite los tokens.
         */
        for (int i = 0; i < 50_000; i++) {
            limitador.permitir("ip|correo-" + i, 5, ventana);
        }

        assertThat(limitador.clavesActivas()).isEqualTo(50_000);

        // La 50.001 ya no se guarda.
        limitador.permitir("ip|correo-nuevo", 5, ventana);
        assertThat(limitador.clavesActivas()).isEqualTo(50_000);
    }

    @Test
    @DisplayName("saturado, deja pasar lo nuevo pero sigue frenando lo que ya contaba")
    void saturadoNoSeConvierteEnLaCaida() {
        LimitadorPeticiones limitador = new LimitadorPeticiones();
        Duration ventana = Duration.ofMinutes(5);

        // Una clave que ya estaba, agotada antes de llenar el mapa.
        assertThat(limitador.permitir("ip|atacante", 1, ventana)).isTrue();
        assertThat(limitador.permitir("ip|atacante", 1, ventana)).isFalse();

        for (int i = 0; i < 50_000; i++) {
            limitador.permitir("ip|correo-" + i, 5, ventana);
        }

        /*
         * Con el mapa lleno, una clave nueva pasa. Es deliberado y en esta
         * dirección: si el limitador empezara a rechazar peticiones legítimas
         * por estar lleno, sería él la caída que trataba de evitar.
         */
        assertThat(limitador.permitir("ip|alguien-que-llega-ahora", 1, ventana)).isTrue();

        // Pero quien ya estaba contado sigue bloqueado: la saturación no es una
        // puerta para saltarse el cupo.
        assertThat(limitador.permitir("ip|atacante", 1, ventana)).isFalse();
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
