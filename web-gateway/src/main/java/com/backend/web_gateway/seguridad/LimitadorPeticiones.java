package com.backend.web_gateway.seguridad;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Cubo de fichas en memoria, contando por clave (IP + ámbito).
 *
 * <h4>Por qué un cubo y no una ventana</h4>
 *
 * <p>Antes era una ventana fija: se contaba hasta el tope y al cumplirse el
 * plazo el contador volvía a cero. Eso deja pasar el doble del cupo en el borde
 * —veinte peticiones en el último segundo de una ventana y otras veinte en el
 * primero de la siguiente son cuarenta seguidas con un cupo de veinte— y es
 * justo el momento que busca quien prueba contraseñas: basta con esperar al
 * cambio de ventana. El comentario decía «ventana deslizante», que es lo que
 * había que hacer y no lo que hacía.
 *
 * <p>El cubo no tiene bordes. Cada clave tiene {@code maximo} fichas y se
 * repone a {@code maximo / duracion} por milisegundo, continuamente. Quien
 * llega de golpe gasta las fichas que haya —la ráfaga legítima sigue
 * permitida—, pero a partir de ahí solo pasa al ritmo de reposición, y no hay
 * ningún instante en el que el cupo se regale entero.
 *
 * <h4>Sigue siendo por instancia, y es una decisión</h4>
 *
 * <p>Con varias réplicas, cada una lleva su cubo y el tope efectivo se
 * multiplica. Compartirlo exigiría un salto de red (Redis, la base) en CADA
 * petición, y ese salto cuesta más que lo que protege: el cupo está para que
 * nadie tumbe el servicio, y cada instancia acota lo que ella misma atiende. Lo
 * que sí es global es el gasto en el proveedor de IA, porque ahí se pierde
 * dinero y no CPU: ver {@code PresupuestoIa}.
 */
@Component
@Slf4j
public class LimitadorPeticiones {

    /** Tope de claves distintas: impide que el propio limitador agote memoria. */
    private static final int MAXIMO_CLAVES = 50_000;

    private final Map<String, Cubo> cubos = new ConcurrentHashMap<>();

    /**
     * Fichas disponibles y cuándo se calcularon. Se reemplaza entero dentro de
     * {@code compute}, que es lo que hace atómico el «mira, repón y gasta».
     */
    private record Cubo(double fichas, Instant momento) {
    }

    /**
     * @return true si la petición cabe en el cupo; false si hay que rechazarla.
     */
    public boolean permitir(String clave, int maximo, Duration duracion) {
        Instant ahora = Instant.now();

        // Si el mapa se desborda, se prefiere dejar pasar antes que quedarse sin
        // memoria: el limitador nunca debe ser el que tumbe el servicio.
        if (cubos.size() >= MAXIMO_CLAVES && !cubos.containsKey(clave)) {
            log.warn("Limitador saturado ({} claves); se permite la petición", cubos.size());
            return true;
        }

        boolean[] permitida = new boolean[1];
        cubos.compute(clave, (k, actual) -> {
            double fichas = actual == null ? maximo
                    : repuestas(actual, ahora, maximo, duracion);
            permitida[0] = fichas >= 1;
            return new Cubo(permitida[0] ? fichas - 1 : fichas, ahora);
        });
        return permitida[0];
    }

    /** Segundos hasta que haya una ficha; 1 como mínimo, para no decir «ya». */
    public long segundosParaReintentar(String clave, Duration duracion) {
        return segundosParaReintentar(clave, 0, duracion);
    }

    /**
     * @param maximo el mismo cupo con el que se rechazó, para saber a qué ritmo
     *               se repone. Con 0 se supone una ficha por ventana, que es el
     *               peor caso y nunca promete antes de tiempo.
     */
    public long segundosParaReintentar(String clave, int maximo, Duration duracion) {
        Cubo cubo = cubos.get(clave);
        if (cubo == null) {
            return 0;
        }
        double porMilisegundo = (double) Math.max(maximo, 1) / duracion.toMillis();
        double faltan = 1 - repuestas(cubo, Instant.now(), Math.max(maximo, 1), duracion);
        if (faltan <= 0) {
            return 0;
        }
        return Math.max(1, (long) Math.ceil(faltan / porMilisegundo / 1000));
    }

    /** Libera el cupo de una clave; se usa tras un login correcto. */
    public void limpiar(String clave) {
        cubos.remove(clave);
    }

    /** Purga los cubos llenos y olvidados para que el mapa no crezca sin fin. */
    @Scheduled(fixedDelay = 300_000)
    public void purgar() {
        Instant limite = Instant.now().minus(Duration.ofMinutes(30));
        int antes = cubos.size();
        cubos.entrySet().removeIf(e -> e.getValue().momento().isBefore(limite));

        int purgadas = antes - cubos.size();
        if (purgadas > 0) {
            log.debug("Purgados {} cubos de rate limit", purgadas);
        }
    }

    /** Para las métricas: cuántas claves se están siguiendo. */
    public int clavesActivas() {
        return cubos.size();
    }

    private static double repuestas(Cubo cubo, Instant ahora, int maximo, Duration duracion) {
        long transcurrido = Duration.between(cubo.momento(), ahora).toMillis();
        double porMilisegundo = (double) maximo / duracion.toMillis();
        return Math.min(maximo, cubo.fichas() + Math.max(0, transcurrido) * porMilisegundo);
    }
}
