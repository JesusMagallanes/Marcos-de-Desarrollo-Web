package com.backend.catalogo.descubrimiento;

import java.time.Duration;
import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * El proceso que construye las dos capas colaborativas.
 *
 * <p>Va por lotes y no en la petición por una razón de tamaño: la co-visita es
 * una agregación sobre TODOS los eventos de la ventana, y el Home tiene que
 * poder leer el resultado con un recorrido de índice. Calcularlo en cada
 * {@code GET /home} sería pagar el histórico entero por visitante.
 *
 * <p>Cada hora, y no más seguido, porque el gusto colectivo no cambia por
 * minutos: entre dos pasadas la diferencia sería ruido. La frecuencia se
 * configura sin tocar código.
 *
 * <h4>Idempotente</h4>
 *
 * <p>Ejecutarlo dos veces sobre los mismos datos deja exactamente el mismo
 * resultado. Las dos consultas terminan en {@code ON CONFLICT DO UPDATE}, de
 * modo que reescriben en vez de duplicar, y la purga posterior borra lo que
 * esta pasada no volvió a tocar. Eso es también lo que hace que una relación
 * pueda morir cuando deja de sostenerse.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ColaborativoService {

    private final ItemRelacionRepository relaciones;
    private final SujetoSimilitudRepository similitudes;
    private final MetricasDescubrimiento metricas;
    private final PesosDescubrimiento pesos;

    /** Lo que la última pasada dejó hecho. Para métricas y para las pruebas. */
    public record Resultado(int relacionesItem, int relacionesSujeto,
            int itemObsoletas, int sujetoObsoletas) {
    }

    /**
     * La pasada periódica.
     *
     * <p>Lleva {@code @Transactional} y NO es redundante con la de
     * {@link #recalcular()}, aunque lo parezca. Sin ella esto fallaba cada vez
     * que saltaba el reloj, con «No active transaction for update or delete
     * query», y las tablas colaborativas se quedaban vacías para siempre.
     *
     * <p>El motivo es la autoinvocación: el planificador entra por el proxy,
     * pero de ahí en adelante {@code recalcular()} se llama sobre {@code this},
     * que ya es el objeto real. El proxy no vuelve a intervenir y la anotación
     * de dentro no llega a aplicarse nunca. Poniéndola aquí, la transacción se
     * abre en la única llamada que sí atraviesa el proxy.
     *
     * <p>No lo veía ninguna prueba: las de integración llaman a
     * {@code recalcular()} desde fuera, o sea a través del proxy, que es
     * justamente el camino que en producción no se toma. Lo destapó el
     * navegador. Ver {@code ColaborativoProgramadoTest}.
     */
    @Scheduled(fixedDelayString = "${descubrimiento.colaborativo.intervalo-ms:3600000}",
            initialDelayString = "${descubrimiento.colaborativo.retraso-inicial-ms:180000}")
    @Transactional
    public void programado() {
        Resultado r = recalcular();
        /*
         * Sin identificadores de nadie: son cuentas agregadas. Un log de CI es
         * publico y un log de produccion lo lee mucha mas gente de la que cree
         * quien lo escribe.
         */
        log.info("Colaborativo recalculado: {} relaciones entre productos ({} obsoletas),"
                + " {} entre perfiles ({} obsoletas)",
                r.relacionesItem(), r.itemObsoletas(),
                r.relacionesSujeto(), r.sujetoObsoletas());

        // Un cero sostenido aqui es la senal de que algo se rompio en silencio.
        metricas.loteTerminado(relaciones.count(), similitudes.count());
    }

    /**
     * Reconstruye las dos capas.
     *
     * <p>El corte de la purga es el instante en que empezó ESTA pasada. Todo lo
     * que siga vivo ha sido reescrito con una marca posterior, así que lo que
     * quede por debajo es exactamente lo que ya no se sostiene. Se toma una
     * sola vez y se usa para las dos capas para que no puedan discrepar.
     */
    @Transactional
    public Resultado recalcular() {
        Instant ahora = Instant.now();
        Duration ventana = pesos.ventanaColaborativa();
        Instant desde = ahora.minus(ventana);
        int dias = pesos.getColaborativoVentanaDias();

        int item = relaciones.recalcular(desde,
                pesos.getColaborativoMinSoporte(),
                pesos.getColaborativoTopeItemsPorSujeto(),
                dias, ahora);

        int sujeto = similitudes.recalcular(desde,
                pesos.getSimilitudMinCompartidas(),
                pesos.getSimilitudTopeSujetosPorFaceta(),
                dias, ahora);

        int itemFuera = relaciones.purgarObsoletas(ahora);
        int sujetoFuera = similitudes.purgarObsoletas(ahora);

        return new Resultado(item, sujeto, itemFuera, sujetoFuera);
    }
}
