package com.backend.compras.shared.resiliencia;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;

/** Cortacircuitos mínimo para las llamadas a otros servicios. */
@Slf4j
public class Cortacircuitos {

    public enum Estado {
        // Todo pasa.
        CERRADO,
        // Demasiados fallos: se rechaza sin llamar.
        ABIERTO,
        // Ventana de prueba: se deja pasar una llamada para tantear.
        SEMIABIERTO
    }

    private final String nombre;
    private final int umbralFallos;
    private final Duration esperaApertura;

    private final AtomicReference<Estado> estado = new AtomicReference<>(Estado.CERRADO);
    private final AtomicInteger fallosConsecutivos = new AtomicInteger(0);
    private final AtomicReference<Instant> abiertoDesde = new AtomicReference<>();
    /** Contador de peticiones de prueba en estado SEMIABIERTO. */
    private final AtomicInteger pruebasEnCurso = new AtomicInteger(0);

    public Cortacircuitos(String nombre, int umbralFallos, Duration esperaApertura) {
        this.nombre = nombre;
        this.umbralFallos = umbralFallos;
        this.esperaApertura = esperaApertura;
    }

    public <T> T ejecutar(Supplier<T> operacion) {
        if (!permitePasar()) {
            throw new CircuitoAbiertoException(
                    "El servicio '" + nombre + "' no está respondiendo. Inténtalo en unos minutos.");
        }

        try {
            T resultado = operacion.get();
            alExito();
            return resultado;

        } catch (RuntimeException ex) {
            alFallo();
            throw ex;

        } catch (Error ex) {
            /*
             * Un Error no cuenta como fallo del servicio remoto —no lo es—, pero
             * el contador de pruebas SÍ hay que soltarlo. Solo se pone a cero en
             * alExito y en alFallo, así que sin esto una prueba que muriera por
             * un OutOfMemory o un StackOverflow dejaría el contador en 1 para
             * siempre y el cortacircuitos rechazaría todo lo que viniera detrás,
             * incluso con el servicio ya sano.
             */
            pruebasEnCurso.set(0);
            throw ex;
        }
    }

    private boolean permitePasar() {
        Estado actual = estado.get();

        if (actual == Estado.CERRADO) {
            return true;
        }

        if (actual == Estado.ABIERTO) {
            Instant desde = abiertoDesde.get();
            if (desde != null && Instant.now().isAfter(desde.plus(esperaApertura))) {
                // Se pasa a semiabierto: la siguiente llamada es la de prueba.
                if (estado.compareAndSet(Estado.ABIERTO, Estado.SEMIABIERTO)) {
                    pruebasEnCurso.set(0);
                    log.info("Cortacircuitos '{}' en prueba (semiabierto)", nombre);
                }
                // Solo una peticion de prueba a la vez: si ya hay una en curso,
                // se rechaza para evitar que multiples hilos pasen simultaneamente.
                if (pruebasEnCurso.incrementAndGet() == 1) {
                    return true;
                }
                pruebasEnCurso.decrementAndGet();
                return false;
            }
            return false;
        }

        // SEMIABIERTO: solo una peticion de prueba a la vez.
        if (pruebasEnCurso.incrementAndGet() == 1) {
            return true;
        }
        pruebasEnCurso.decrementAndGet();
        return false;
    }

    private void alExito() {
        fallosConsecutivos.set(0);
        pruebasEnCurso.set(0);
        if (estado.getAndSet(Estado.CERRADO) != Estado.CERRADO) {
            log.info("Cortacircuitos '{}' cerrado: el servicio respondió", nombre);
        }
    }

    private void alFallo() {
        int fallos = fallosConsecutivos.incrementAndGet();
        pruebasEnCurso.set(0);

        if (estado.get() == Estado.SEMIABIERTO || fallos >= umbralFallos) {
            estado.set(Estado.ABIERTO);
            abiertoDesde.set(Instant.now());
            log.warn("Cortacircuitos '{}' ABIERTO tras {} fallos consecutivos", nombre, fallos);
        }
    }

    public Estado estadoActual() {
        return estado.get();
    }
}
