package com.backend.usuarios.shared.seguridad;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** A04 (Diseño inseguro) y A07 (Fallos de autenticación). */
@Component
public class LimitadorPeticiones {

    private record Ventana(Instant inicio, AtomicInteger contador) {
    }

    private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();

    /**
     * @return true si la petición se permite; false si excede el cupo.
     */
    public boolean permitir(String clave, int maximo, Duration ventana) {
        Instant ahora = Instant.now();

        Ventana actual = ventanas.compute(clave, (k, previa) -> {
            if (previa == null || previa.inicio().plus(ventana).isBefore(ahora)) {
                return new Ventana(ahora, new AtomicInteger(0));
            }
            return previa;
        });

        return actual.contador().incrementAndGet() <= maximo;
    }

    /** Segundos que faltan para que se libere el cupo, para la cabecera Retry-After. */
    public long segundosParaReintentar(String clave, Duration ventana) {
        Ventana v = ventanas.get(clave);
        if (v == null) {
            return 0;
        }
        long restante = Duration.between(Instant.now(), v.inicio().plus(ventana)).getSeconds();
        return Math.max(restante, 1);
    }

    public void limpiar(String clave) {
        ventanas.remove(clave);
    }

    /** Evita que el mapa crezca sin límite con IPs que ya no vuelven. */
    @Scheduled(fixedDelay = 300_000)
    void purgar() {
        Instant limite = Instant.now().minus(Duration.ofMinutes(30));
        ventanas.entrySet().removeIf(e -> e.getValue().inicio().isBefore(limite));
    }

    /** Para las métricas: cuántas claves se están siguiendo. */
    public int clavesActivas() {
        return ventanas.size();
    }
}
