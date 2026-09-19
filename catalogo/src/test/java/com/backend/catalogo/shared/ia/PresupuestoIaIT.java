package com.backend.catalogo.shared.ia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import com.backend.catalogo.PruebaIntegracion;

/**
 * El tope de gasto contra Postgres real.
 *
 * <p>Con mocks esto no se prueba: lo que hay que demostrar es que veinte hilos
 * —que aquí hacen de réplicas— pidiendo a la vez no se llevan más fichas de las
 * que hay. Eso depende de que el UPSERT sea una sola sentencia y de que Postgres
 * serialice la fila, no de código Java.
 */
@EnabledIf(
        value = "com.backend.catalogo.Docker#disponible",
        disabledReason = "Docker no está disponible: se omiten las pruebas de integración")
@DisplayName("Tope de gasto de IA compartido")
class PresupuestoIaIT extends PruebaIntegracion {

    @Autowired
    private PresupuestoIa presupuesto;

    @Autowired
    private PresupuestoIaRepository repositorio;

    @BeforeEach
    void limpiar() {
        repositorio.deleteAll();
    }

    @Test
    @DisplayName("cuenta las llamadas y corta al llegar al tope")
    void cuentaYCorta() {
        assertThat(presupuesto.hayCupo(3)).isTrue();
        assertThat(presupuesto.hayCupo(3)).isTrue();
        assertThat(presupuesto.hayCupo(3)).isTrue();

        assertThat(presupuesto.hayCupo(3)).isFalse();
        assertThat(presupuesto.hayCupo(3)).isFalse();
    }

    @Test
    @DisplayName("con el tope en cero no hay tope: la IA no se apaga por olvidar configurarlo")
    void sinTope() {
        for (int i = 0; i < 50; i++) {
            assertThat(presupuesto.hayCupo(0)).isTrue();
        }
        assertThat(repositorio.count()).as("ni siquiera toca la base").isZero();
    }

    @Test
    @DisplayName("veinte peticiones a la vez no se llevan mas de diez fichas")
    void sinCarreraEntreReplicas() throws Exception {
        int tope = 10;
        int simultaneas = 20;

        try (ExecutorService hilos = Executors.newFixedThreadPool(simultaneas)) {
            List<Callable<Boolean>> tareas = IntStream.range(0, simultaneas)
                    .<Callable<Boolean>>mapToObj(i -> () -> presupuesto.hayCupo(tope))
                    .toList();

            long permitidas = hilos.invokeAll(tareas).stream()
                    .map(PresupuestoIaIT::resultado)
                    .filter(Boolean::booleanValue)
                    .count();

            assertThat(permitidas).as("el contador esta en la base, no en cada proceso")
                    .isEqualTo(tope);
        }
    }

    private static boolean resultado(Future<Boolean> f) {
        try {
            return f.get();
        } catch (InterruptedException interrumpido) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrumpido);
        } catch (Exception fallo) {
            throw new IllegalStateException(fallo);
        }
    }
}
