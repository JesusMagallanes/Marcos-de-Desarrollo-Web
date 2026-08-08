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
                    log.info("Cortacircuitos '{}' en prueba (semiabierto)", nombre);
                }
                return true;
            }
            return false;
        }

        // SEMIABIERTO: se deja pasar para ver si el servicio volvió.
        return true;
    }

    private void alExito() {
        fallosConsecutivos.set(0);
        if (estado.getAndSet(Estado.CERRADO) != Estado.CERRADO) {
            log.info("Cortacircuitos '{}' cerrado: el servicio respondió", nombre);
        }
    }

    private void alFallo() {
        int fallos = fallosConsecutivos.incrementAndGet();

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
