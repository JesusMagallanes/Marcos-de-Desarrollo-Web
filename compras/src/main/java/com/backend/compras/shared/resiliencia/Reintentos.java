package com.backend.compras.shared.resiliencia;

import java.time.Duration;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;

/** Reintento con espera exponencial para fallos transitorios de red. */
@Slf4j
public final class Reintentos {

    private Reintentos() {
    }

    public static <T> T conEsperaExponencial(String operacion, int maxIntentos, Supplier<T> tarea) {
        RuntimeException ultimo = null;

        for (int intento = 1; intento <= maxIntentos; intento++) {
            try {
                return tarea.get();

            } catch (CircuitoAbiertoException ex) {
                // El circuito está abierto: insistir solo empeora las cosas.
                throw ex;

            } catch (RuntimeException ex) {
                ultimo = ex;
                if (intento == maxIntentos) {
                    break;
                }

                long esperaMs = (long) Math.pow(2, intento - 1) * 200;
                log.warn("'{}' falló (intento {}/{}), reintento en {} ms: {}",
                        operacion, intento, maxIntentos, esperaMs, ex.getMessage());
                dormir(esperaMs);
            }
        }

        throw ultimo;
    }

    private static void dormir(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Reintento interrumpido", ex);
        }
    }
}
