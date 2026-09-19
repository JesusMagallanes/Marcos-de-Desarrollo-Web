package com.backend.catalogo.shared.ia;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * El tope de llamadas al modelo, contado entre todas las réplicas.
 *
 * <h4>Qué arregla</h4>
 *
 * <p>El tope vivía en un contador del proceso. Con tres réplicas, cada una se
 * creía dueña del cupo entero y el gasto real era el triple del configurado. Un
 * cupo de CPU puede ser por instancia —cada una acota lo que ella atiende—,
 * pero una factura es una sola.
 *
 * <h4>Por qué la base y no Redis</h4>
 *
 * <p>Porque la base ya está, ya es transaccional y el contador cambia como mucho
 * un par de veces por segundo: el viaje son milisegundos al lado de una llamada
 * al modelo que tarda segundos. Redis sería un servicio más que desplegar y
 * vigilar para eso. Distinto sería compartir el cupo por IP, que son miles de
 * escrituras por minuto; ver {@code LimitadorPeticiones}.
 *
 * <h4>Si la base no contesta, no se deja de cobrar el tope</h4>
 *
 * <p>Caer a «permitido» dejaría el gasto suelto justo cuando algo va mal, y
 * caer a «denegado» apagaría la IA por un problema que no es suyo. Así que se
 * cae al contador en memoria de esta instancia: el tope vuelve a ser por
 * réplica —lo que había antes— en lugar de desaparecer.
 */
@Component
@Slf4j
public class PresupuestoIa {

    /** Ámbito del tope. Una fila fija: no crece con el tráfico. */
    private static final String CLAVE = "global";

    private final PresupuestoIaRepository repositorio;

    /** Respaldo por instancia para cuando la base no está disponible. */
    private final AtomicLong minutoLocal = new AtomicLong(-1);
    private final AtomicInteger usadasLocal = new AtomicInteger();

    public PresupuestoIa(PresupuestoIaRepository repositorio) {
        this.repositorio = repositorio;
    }

    /**
     * Apunta una llamada y dice si cabe en el cupo.
     *
     * <p>Sin transacción aquí a propósito: la abre el repositorio, y así un
     * fallo de base se queda en la suya. Si la abriera este método, el fallo la
     * marcaría como «solo rollback» y el respaldo en memoria no serviría de
     * nada —al terminar, el commit reventaría igual.
     *
     * @param maxPorMinuto 0 o menos quita el tope
     */
    public boolean hayCupo(int maxPorMinuto) {
        if (maxPorMinuto <= 0) {
            return true;
        }
        long minuto = System.currentTimeMillis() / 60_000;
        try {
            return repositorio.anotarLlamada(CLAVE, minuto) <= maxPorMinuto;
        } catch (RuntimeException fallo) {
            log.warn("Presupuesto de IA sin base ({}); se cuenta solo en esta instancia",
                    fallo.getClass().getSimpleName());
            return cupoLocal(minuto, maxPorMinuto);
        }
    }

    private boolean cupoLocal(long minuto, int maxPorMinuto) {
        if (minutoLocal.getAndSet(minuto) != minuto) {
            usadasLocal.set(0);
        }
        return usadasLocal.incrementAndGet() <= maxPorMinuto;
    }
}
