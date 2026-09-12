package com.backend.catalogo.descubrimiento;

import java.time.Instant;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;

import lombok.extern.slf4j.Slf4j;

/**
 * Borra el histórico de eventos que Discovery ya no necesita.
 *
 * <h4>Lo que había</h4>
 *
 * <p>Nada. {@code evento_interaccion} es un log de solo-anexado con retención
 * decidida en V21 —noventa días— y un método de purga escrito en la fase 1 al
 * que nadie llamaba. La tabla crecía sin techo mientras los agregados que
 * sobreviven a la purga se calculaban igual.
 *
 * <h4>Qué se puede borrar sin romper nada</h4>
 *
 * <p>La auditoría lo recorrió consumidor a consumidor. La sesión mira 24 horas;
 * tendencias, 14 días; colaborativo, 30; la medición, lo servido en los últimos
 * 90 más un día de atribución. El perfil se deriva al ingerir y nunca se
 * reconstruye desde los eventos; el cooldown lee impresiones, no eventos. Con
 * 90 días de retención, ningún proceso activo se queda sin datos y sobra
 * margen.
 *
 * <h4>Por lotes y con tope</h4>
 *
 * <p>Cada lote va en su propia transacción y se confirma solo. La primera
 * pasada tras desplegar esto se encuentra con todo el atraso acumulado, y un
 * borrado de golpe sería una transacción de minutos que retiene bloqueos y, si
 * se cae, deshace todo. El tope por ejecución acota cuánto trabajo hace una
 * pasada; lo que quede lo termina la siguiente. Ninguna de las dos cosas
 * cambia el resultado final, solo cuánto tarda en llegar.
 *
 * <h4>Idempotente por naturaleza</h4>
 *
 * <p>Borra lo que es más viejo que un instante. Ejecutarla dos veces con el
 * mismo instante borra en la segunda exactamente cero filas: no hay estado que
 * pueda duplicarse ni resultado que dependa de cuántas veces se llamó.
 */
@Service
@Slf4j
public class PurgaEventosService {

    private final EventoInteraccionRepository eventos;
    private final MetricasDescubrimiento metricas;
    private final PesosDescubrimiento pesos;

    /*
     * Para que cada lote atraviese el proxy y estrene transaccion. Una llamada
     * sobre `this` se saltaria la anotacion, y entonces todos los lotes irian
     * juntos en la transaccion del llamante — justo lo que se quiere evitar.
     */
    private final ObjectProvider<PurgaEventosService> self;

    public PurgaEventosService(EventoInteraccionRepository eventos,
            MetricasDescubrimiento metricas, PesosDescubrimiento pesos,
            ObjectProvider<PurgaEventosService> self) {
        this.eventos = eventos;
        this.metricas = metricas;
        this.pesos = pesos;
        this.self = self;
    }

    /** Lo que hizo una pasada. */
    public record Resultado(int purgadas, int lotes, boolean quedaPendiente) {
    }

    /**
     * Purga todo lo anterior al corte, en lotes, hasta el tope de la pasada.
     *
     * <p>NO es transaccional a propósito: cada lote abre y cierra la suya.
     *
     * @param corte los eventos anteriores a este instante se van; la frontera
     *     es la misma que ya usan servidas e impresiones, estricta por abajo
     */
    public Resultado purgar(Instant corte) {
        int lote = pesos.getPurgaLote();
        int tope = pesos.getPurgaMaximoPorEjecucion();

        int total = 0;
        int lotes = 0;
        boolean pendiente = false;

        while (total < tope) {
            int cuantos = Math.min(lote, tope - total);
            int borradas = self.getObject().lote(corte, cuantos);
            lotes++;
            total += borradas;

            if (borradas < cuantos) {
                // Un lote incompleto significa que ya no queda nada vencido.
                break;
            }
            if (total >= tope) {
                // Hay mas; se deja para la siguiente pasada en vez de seguir.
                pendiente = true;
            }
        }

        if (total > 0) {
            metricas.purgadas("evento_interaccion", total);
            /*
             * Cuentas y nada mas: ni sujetos, ni items, ni fechas concretas.
             * Un log es lo mas facil de exportar sin querer.
             */
            log.info("Purga de eventos: {} filas en {} lotes{}", total, lotes,
                    pendiente ? " (queda atraso para la siguiente pasada)" : "");
        }
        return new Resultado(total, lotes, pendiente);
    }

    /**
     * Un lote, en transacción propia.
     *
     * <p>Público solo porque tiene que pasar por el proxy. No se llama desde
     * fuera: la puerta es {@link #purgar}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int lote(Instant corte, int cuantos) {
        return eventos.purgarLote(corte, cuantos);
    }
}
