package com.backend.compras.shared.resiliencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.backend.compras.shared.resiliencia.Cortacircuitos.Estado;

class CortacircuitosTest {

    private Cortacircuitos nuevo(Duration espera) {
        return new Cortacircuitos("prueba", 3, espera);
    }

    @Test
    @DisplayName("con el circuito cerrado la llamada pasa")
    void pasaEstandoCerrado() {
        Cortacircuitos cb = nuevo(Duration.ofSeconds(1));
        assertThat(cb.ejecutar(() -> "ok")).isEqualTo("ok");
        assertThat(cb.estadoActual()).isEqualTo(Estado.CERRADO);
    }

    @Test
    @DisplayName("tras el umbral de fallos se abre y deja de llamar al destino")
    void abreTrasElUmbral() {
        Cortacircuitos cb = nuevo(Duration.ofMinutes(1));

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> cb.ejecutar(() -> {
                throw new IllegalStateException("caído");
            })).isInstanceOf(IllegalStateException.class);
        }

        assertThat(cb.estadoActual()).isEqualTo(Estado.ABIERTO);

        // Ya no se invoca la operación: falla al instante.
        boolean[] invocada = { false };
        assertThatThrownBy(() -> cb.ejecutar(() -> {
            invocada[0] = true;
            return "no debería llegar";
        })).isInstanceOf(CircuitoAbiertoException.class);

        assertThat(invocada[0]).as("la operación no debe ejecutarse").isFalse();
    }

    @Test
    @DisplayName("un éxito reinicia el contador de fallos consecutivos")
    void elExitoReinicia() {
        Cortacircuitos cb = nuevo(Duration.ofMinutes(1));

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> cb.ejecutar(() -> {
                throw new IllegalStateException("fallo transitorio");
            })).isInstanceOf(IllegalStateException.class);
        }

        cb.ejecutar(() -> "recuperado");

        // Otros dos fallos no deben bastar si el contador se reinició.
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> cb.ejecutar(() -> {
                throw new IllegalStateException("fallo");
            })).isInstanceOf(IllegalStateException.class);
        }
        assertThat(cb.estadoActual()).isEqualTo(Estado.CERRADO);
    }

    @Test
    @DisplayName("pasada la espera tantea con una llamada y se cierra si funciona")
    void semiabiertoYRecuperacion() throws InterruptedException {
        Cortacircuitos cb = nuevo(Duration.ofMillis(80));

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> cb.ejecutar(() -> {
                throw new IllegalStateException("caído");
            })).isInstanceOf(IllegalStateException.class);
        }
        assertThat(cb.estadoActual()).isEqualTo(Estado.ABIERTO);

        Thread.sleep(120);

        assertThat(cb.ejecutar(() -> "vivo")).isEqualTo("vivo");
        assertThat(cb.estadoActual()).isEqualTo(Estado.CERRADO);
    }

    @Test
    @DisplayName("si la llamada de prueba falla, vuelve a abrirse de inmediato")
    void semiabiertoQueFalla() throws InterruptedException {
        Cortacircuitos cb = nuevo(Duration.ofMillis(80));

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> cb.ejecutar(() -> {
                throw new IllegalStateException("caído");
            })).isInstanceOf(IllegalStateException.class);
        }

        Thread.sleep(120);

        assertThatThrownBy(() -> cb.ejecutar(() -> {
            throw new IllegalStateException("sigue caído");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(cb.estadoActual()).isEqualTo(Estado.ABIERTO);
    }
}
