package com.backend.catalogo.descubrimiento;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;

/**
 * Qué está mirando esta persona AHORA, que no es lo mismo que qué le interesa.
 *
 * <p>El perfil de {@code perfil_faceta} describe un gusto: se construye con
 * meses y olvida despacio, con una vida media de siete días. Eso es lo correcto
 * para saber que a alguien le gustan los monitores. Y es justo lo que no sirve
 * cuando esa misma persona entra hoy buscando una impresora: el perfil sigue
 * diciendo «monitores» y tiene razón, pero no es la pregunta.
 *
 * <p>De ahí las tres señales. El HISTÓRICO es el perfil de siempre, intacto. El
 * RECIENTE es lo de las últimas horas, que el olvido aún no ha tocado. La
 * INTENCIÓN DE SESIÓN es lo que está pasando en esta visita concreta, y es la
 * que puede contradecir a las otras dos sin llevarse por delante ninguna.
 *
 * <h4>La sesión se deduce, no se recibe</h4>
 *
 * <p>El identificador de sesión lo genera el navegador y solo viaja en la
 * ingesta: las peticiones de lectura no lo llevan. Así que la sesión activa se
 * deduce del último evento del sujeto, si es lo bastante reciente. No es un
 * apaño: evita cambiar el contrato, evita tocar el cliente y hace imposible que
 * alguien mande la sesión de otro.
 *
 * <h4>Sin tabla nueva</h4>
 *
 * <p>Todo sale de {@code evento_interaccion}, que ya tiene sujeto, sesión,
 * fecha y tipo. Persistir un «perfil de sesión» habría sido guardar algo que se
 * puede calcular, que caduca en media hora y que además es el dato más
 * sensible de todos: lo que alguien está mirando en este momento.
 */
@Service
@RequiredArgsConstructor
public class SesionService {

    /** Cuánto cuesta deducir la intención. Ver el bloque G para el resto. */
    public static final String TIEMPO = "smartzone_descubrimiento_sesion_segundos";

    private final EventoInteraccionRepository eventos;
    private final PesosDescubrimiento pesos;
    private final MeterRegistry registro;

    /**
     * Las señales temporales de un sujeto.
     *
     * @param categorias categorías ordenadas por intención de sesión y, a
     *     igualdad, por interés reciente
     * @param interacciones cuántas interacciones sostienen la sesión; por debajo
     *     del mínimo no hay intención que valga
     */
    public record Intencion(List<Faceta> categorias, int interacciones) {

        /** Una categoría con sus dos pesos temporales. */
        public record Faceta(Long categoriaId, double reciente, double sesion) {
        }

        public static Intencion vacia() {
            return new Intencion(List.of(), 0);
        }

        /** Si hay señal suficiente para dejar que esto influya en algo. */
        public boolean hayIntencion() {
            return !categorias.isEmpty();
        }

        /** Las categorías que el sujeto está mirando ahora, las más fuertes primero. */
        public List<Long> categoriasDeSesion(int cuantas) {
            return categorias.stream()
                    .filter(f -> f.sesion() > 0)
                    .limit(cuantas)
                    .map(Faceta::categoriaId)
                    .toList();
        }
    }

    /**
     * Deduce qué está explorando este sujeto ahora mismo.
     *
     * <p>Dos consultas y no más: una para saber qué sesión sigue viva y otra
     * para agregar las señales. Ninguna por evento ni por candidato.
     *
     * @return {@link Intencion#vacia()} si no hay sesión viva o si la evidencia
     *     no llega al mínimo — que es la respuesta correcta, no un fallo: sin
     *     señal el sistema hace lo que hacía antes
     */
    @Transactional(readOnly = true)
    public Intencion intencionDe(UUID sujeto) {
        if (sujeto == null) {
            return Intencion.vacia();
        }
        Timer cronometro = Timer.builder(TIEMPO)
                .description("Tiempo de deducir la intencion de sesion")
                .register(registro);

        return cronometro.record(() -> calcular(sujeto));
    }

    private Intencion calcular(UUID sujeto) {
        Instant ahora = Instant.now();
        Instant viva = ahora.minus(Duration.ofMinutes(pesos.getSesionVentanaMinutos()));

        UUID sesion = eventos.sesionViva(sujeto, viva);
        if (sesion == null) {
            // Nadie navegando: ni intención de sesión ni nada que deducir.
            return Intencion.vacia();
        }

        /*
         * La ventana de lectura es la del interés reciente, que es la mas larga
         * de las dos: la sesión viva cabe dentro por definición. Asi una sola
         * consulta sirve para las dos señales.
         */
        Instant desde = ahora.minus(Duration.ofHours(pesos.getInteresRecienteHoras()));
        Instant desdeReciente = desde;

        List<Intencion.Faceta> facetas = new ArrayList<>();
        int interacciones = 0;

        for (Object[] fila : eventos.senalesTemporales(
                sujeto, desde, desdeReciente, ahora, sesion)) {

            facetas.add(new Intencion.Faceta(
                    ((Number) fila[0]).longValue(),
                    ((Number) fila[1]).doubleValue(),
                    ((Number) fila[2]).doubleValue()));

            // Interacciones, no categorias: dos fichas de monitores son dos.
            interacciones += ((Number) fila[3]).intValue();
        }

        if (interacciones < pesos.getSesionMinimasInteracciones()) {
            /*
             * Una sola interacción no es una intención: es un clic. Dejarla
             * mandar convertiría el Home en un monográfico por un resbalón, y
             * caer al comportamiento de siempre es mejor que personalizar mal.
             */
            return Intencion.vacia();
        }
        return new Intencion(facetas, interacciones);
    }
}
