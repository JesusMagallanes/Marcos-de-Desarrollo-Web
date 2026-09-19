package com.backend.web_gateway.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El limitador del gateway es la primera barrera frente a abuso desde internet.
 * Si falla, el tráfico llega íntegro a los servicios.
 */
class LimitadorPeticionesTest {

    @Test
    @DisplayName("permite hasta el cupo y bloquea a partir de ahí")
    void respetaElCupo() {
        LimitadorPeticiones limitador = new LimitadorPeticiones();
        Duration ventana = Duration.ofMinutes(5);

        for (int i = 1; i <= 20; i++) {
            assertThat(limitador.permitir("ip|auth", 20, ventana))
                    .as("petición %d de 20", i).isTrue();
        }

        assertThat(limitador.permitir("ip|auth", 20, ventana)).isFalse();
    }

    @Test
    @DisplayName("cada clave lleva su cuenta: bloquear a una IP no afecta a otra")
    void clavesIndependientes() {
        LimitadorPeticiones limitador = new LimitadorPeticiones();
        Duration ventana = Duration.ofMinutes(5);

        limitador.permitir("atacante|auth", 1, ventana);
        assertThat(limitador.permitir("atacante|auth", 1, ventana)).isFalse();

        assertThat(limitador.permitir("legitimo|auth", 1, ventana)).isTrue();
    }

    @Test
    @DisplayName("los ámbitos no se mezclan: agotar lecturas no bloquea el login")
    void ambitosIndependientes() {
        LimitadorPeticiones limitador = new LimitadorPeticiones();
        Duration ventana = Duration.ofMinutes(1);

        limitador.permitir("ip|lectura", 1, ventana);
        assertThat(limitador.permitir("ip|lectura", 1, ventana)).isFalse();

        assertThat(limitador.permitir("ip|autenticacion", 1, ventana)).isTrue();
    }

    @Test
    @DisplayName("al reponerse las fichas el cupo vuelve")
    void ventanaDeslizante() throws InterruptedException {
        LimitadorPeticiones limitador = new LimitadorPeticiones();
        Duration ventana = Duration.ofMillis(100);

        assertThat(limitador.permitir("ip|x", 1, ventana)).isTrue();
        assertThat(limitador.permitir("ip|x", 1, ventana)).isFalse();

        Thread.sleep(150);

        assertThat(limitador.permitir("ip|x", 1, ventana)).isTrue();
    }

    @Test
    @DisplayName("informa cuántos segundos faltan para reintentar")
    void tiempoDeEspera() {
        LimitadorPeticiones limitador = new LimitadorPeticiones();
        Duration ventana = Duration.ofSeconds(30);

        limitador.permitir("ip|x", 1, ventana);

        assertThat(limitador.segundosParaReintentar("ip|x", ventana)).isBetween(1L, 30L);
        // Una clave que nunca se usó no hace esperar.
        assertThat(limitador.segundosParaReintentar("otra", ventana)).isZero();
    }

    @Test
    @DisplayName("limpiar() libera el cupo tras un login correcto")
    void limpiezaTrasExito() {
        LimitadorPeticiones limitador = new LimitadorPeticiones();
        Duration ventana = Duration.ofMinutes(5);

        limitador.permitir("ip|auth", 1, ventana);
        assertThat(limitador.permitir("ip|auth", 1, ventana)).isFalse();

        limitador.limpiar("ip|auth");

        assertThat(limitador.permitir("ip|auth", 1, ventana)).isTrue();
    }

    /**
     * El caso que de verdad importa: muchas peticiones simultáneas desde la
     * misma IP. Si el conteo no fuera atómico, se colarían de más.
     */
    @Test
    @DisplayName("bajo concurrencia no deja pasar más peticiones que el cupo")
    void esSeguroBajoConcurrencia() throws InterruptedException {
        LimitadorPeticiones limitador = new LimitadorPeticiones();
        Duration ventana = Duration.ofMinutes(5);

        int hilos = 50;
        int porHilo = 20;
        int cupo = 100;

        AtomicInteger permitidas = new AtomicInteger();
        CountDownLatch salida = new CountDownLatch(1);
        CountDownLatch fin = new CountDownLatch(hilos);

        try (ExecutorService pool = Executors.newFixedThreadPool(hilos)) {
            for (int h = 0; h < hilos; h++) {
                pool.submit(() -> {
                    try {
                        salida.await();
                        for (int i = 0; i < porHilo; i++) {
                            if (limitador.permitir("ip|carga", cupo, ventana)) {
                                permitidas.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        fin.countDown();
                    }
                });
            }

            salida.countDown();
            assertThat(fin.await(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(permitidas.get())
                .as("1000 intentos concurrentes con cupo 100")
                .isEqualTo(cupo);
    }

    @Test
    @DisplayName("la purga libera las ventanas caducadas")
    void purga() {
        LimitadorPeticiones limitador = new LimitadorPeticiones();

        for (int i = 0; i < 100; i++) {
            limitador.permitir("ip-" + i + "|x", 10, Duration.ofMinutes(1));
        }
        assertThat(limitador.clavesActivas()).isEqualTo(100);

        // Recién creadas, la purga (que borra las de más de 30 min) no las toca.
        limitador.purgar();
        assertThat(limitador.clavesActivas()).isEqualTo(100);
    }

    @Test
    @DisplayName("no regala el cupo entero al cambiar de ventana: es un cubo, no una ventana fija")
    void sinRafagaEnElBorde() throws InterruptedException {
        LimitadorPeticiones limitador = new LimitadorPeticiones();
        Duration ventana = Duration.ofSeconds(2);

        // Calentar: la primera llamada de la clase carga clases y tarda lo
        // bastante como para reponer fichas y falsear la medida.
        for (int i = 0; i < 100; i++) {
            limitador.permitir("ip|calentar", 1000, ventana);
        }

        /*
         * El agujero que tenia la version anterior: gastar el cupo al final de
         * una ventana y volver a gastarlo entero al principio de la siguiente,
         * o sea el doble seguido. Con reposicion continua, tras esperar media
         * ventana solo se ha repuesto la mitad.
         */
        for (int i = 0; i < 20; i++) {
            assertThat(limitador.permitir("ip|prueba", 20, ventana)).isTrue();
        }
        assertThat(limitador.permitir("ip|prueba", 20, ventana)).isFalse();

        Thread.sleep(ventana.dividedBy(2).toMillis());

        int permitidas = 0;
        for (int i = 0; i < 20; i++) {
            if (limitador.permitir("ip|prueba", 20, ventana)) {
                permitidas++;
            }
        }
        assertThat(permitidas).as("media ventana repone media carga, no la entera")
                .isBetween(7, 13);
    }

    @Test
    @DisplayName("una rafaga legitima cabe: el cubo empieza lleno")
    void laRafagaLegitimaPasa() {
        LimitadorPeticiones limitador = new LimitadorPeticiones();

        for (int i = 0; i < 20; i++) {
            assertThat(limitador.permitir("ip|lectura", 20, Duration.ofMinutes(1))).isTrue();
        }
    }

    @Test
    @DisplayName("el tiempo de espera sale del ritmo de reposicion, no de la ventana entera")
    void esperaSegunElRitmo() {
        LimitadorPeticiones limitador = new LimitadorPeticiones();
        Duration ventana = Duration.ofSeconds(60);

        for (int i = 0; i < 60; i++) {
            limitador.permitir("ip|lectura", 60, ventana);
        }
        assertThat(limitador.permitir("ip|lectura", 60, ventana)).isFalse();

        // 60 por minuto es una ficha por segundo: esperar un minuto entero
        // seria mentirle al cliente.
        assertThat(limitador.segundosParaReintentar("ip|lectura", 60, ventana)).isEqualTo(1);
    }
}
