package com.backend.usuarios.shared.seguridad;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/** A04 (Diseño inseguro) y A07 (Fallos de autenticación). */
@Component
@Slf4j
public class LimitadorPeticiones {

    /**
     * Tope de claves distintas: impide que el propio limitador agote memoria.
     *
     * <p>Faltaba justo aquí, en el servicio del login, y estaba puesto en los
     * otros tres. La purga de abajo no basta: descarta lo que lleve más de media
     * hora y corre cada cinco minutos, así que entre pasada y pasada el mapa
     * crece sin freno. Y las claves las controla quien ataca —son la IP y el
     * correo— de modo que media hora de intentos con correos distintos deja una
     * entrada por cada uno. El servicio que se quedaba sin memoria era el que
     * emite los tokens.
     */
    private static final int MAXIMO_CLAVES = 50_000;

    private record Ventana(Instant inicio, AtomicInteger contador) {
    }

    private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();

    /**
     * @return true si la petición se permite; false si excede el cupo.
     */
    public boolean permitir(String clave, int maximo, Duration ventana) {
        Instant ahora = Instant.now();

        /*
         * Desbordado el tope, se deja pasar en vez de rechazar.
         *
         * Es deliberado y va en esta dirección: el limitador protege de un
         * abuso, pero si él mismo empieza a rechazar peticiones legítimas por
         * estar lleno, se convierte en la caída que intentaba evitar. Las claves
         * que ya existen siguen contándose con normalidad, así que un atacante
         * que insiste sobre la misma sigue bloqueado; lo que se pierde es la
         * cuenta de las nuevas mientras dura la saturación, y eso queda en el
         * log para que se vea.
         */
        if (ventanas.size() >= MAXIMO_CLAVES && !ventanas.containsKey(clave)) {
            log.warn("Limitador saturado ({} claves); se permite la petición", ventanas.size());
            return true;
        }

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
