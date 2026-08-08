package com.backend.catalogo.shared.seguridad;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/** Ventana deslizante en memoria, contando por clave (IP + ámbito). */
@Component
@Slf4j
public class LimitadorPeticiones {

    /** Tope de claves distintas: impide que el propio limitador agote memoria. */
    private static final int MAXIMO_CLAVES = 50_000;

    private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();

    private record Ventana(Instant inicio, AtomicInteger conteo) {
    }

    /**
     * @return true si la petición cabe en el cupo; false si hay que rechazarla.
     */
    public boolean permitir(String clave, int maximo, Duration duracion) {
        Instant ahora = Instant.now();

        // Si el mapa se desborda, se prefiere dejar pasar antes que quedarse sin
        // memoria: el limitador nunca debe ser el que tumbe el servicio.
        if (ventanas.size() >= MAXIMO_CLAVES && !ventanas.containsKey(clave)) {
            log.warn("Limitador saturado ({} claves); se permite la petición", ventanas.size());
            return true;
        }

        Ventana ventana = ventanas.compute(clave, (k, actual) -> {
            if (actual == null || ahora.isAfter(actual.inicio().plus(duracion))) {
                return new Ventana(ahora, new AtomicInteger(0));
            }
            return actual;
        });

        return ventana.conteo().incrementAndGet() <= maximo;
    }

    /** Segundos que faltan para que la ventana se renueve. */
    public long segundosParaReintentar(String clave, Duration duracion) {
        Ventana ventana = ventanas.get(clave);
        if (ventana == null) {
            return 0;
        }
        long restante = Duration.between(Instant.now(), ventana.inicio().plus(duracion)).getSeconds();
        return Math.max(1, restante);
    }

    /** Libera el cupo de una clave; se usa tras un login correcto. */
    public void limpiar(String clave) {
        ventanas.remove(clave);
    }

    /** Purga las ventanas caducadas para que el mapa no crezca sin fin. */
    @Scheduled(fixedDelay = 300_000)
    public void purgar() {
        Instant limite = Instant.now().minus(Duration.ofMinutes(30));
        int antes = ventanas.size();
        ventanas.entrySet().removeIf(e -> e.getValue().inicio().isBefore(limite));

        int purgadas = antes - ventanas.size();
        if (purgadas > 0) {
            log.debug("Purgadas {} ventanas de rate limit", purgadas);
        }
    }

    /** Para las métricas: cuántas claves se están siguiendo. */
    public int clavesActivas() {
        return ventanas.size();
    }
}
