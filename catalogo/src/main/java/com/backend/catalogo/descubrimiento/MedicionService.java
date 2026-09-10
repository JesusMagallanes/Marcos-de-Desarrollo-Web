package com.backend.catalogo.descubrimiento;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Convierte el detalle de lo servido en cuentas que se pueden mirar.
 *
 * <p>La agregación va por lotes por la misma razón que todo lo demás: un panel
 * que recorriera millones de filas en cada consulta acabaría costando más que
 * servir la tienda. Aquí queda una fila por día, módulo, razón, versión y banda
 * de posición —unas decenas al día— que se pueden conservar durante años.
 *
 * <h4>Se recalculan DOS días, no uno</h4>
 *
 * <p>El de ayer y el de hoy. El de hoy porque va cambiando; el de ayer porque
 * la atribución de acciones posteriores necesita tiempo para cerrarse: alguien
 * que ve una recomendación a las 23:50 y compra a las 00:30 pertenece al día
 * anterior, y si solo se calculara una vez ese acierto no se contaría jamás.
 * Como la agregación es idempotente —{@code ON CONFLICT DO UPDATE}— repetir un
 * día no duplica nada, solo lo corrige.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MedicionService {

    private final RecomendacionServidaRepository servidas;
    private final ImpresionRepository impresiones;
    private final MetricaDescubrimientoRepository metricas;
    private final MetricasDescubrimiento indicadores;
    private final PesosDescubrimiento pesos;

    /**
     * Ventana en la que una acción se atribuye a la recomendación que la
     * precedió.
     *
     * <p>Configurable a propósito. No es perfecta —alguien pudo llegar al
     * producto por el buscador y no por el carrusel— así que acortarla atribuye
     * de menos y alargarla atribuye de más. Lo que la hace útil no es acertar,
     * es ser la MISMA para todos los módulos: así la comparación entre ellos
     * significa algo aunque el número absoluto no sea exacto.
     */
    @Value("${descubrimiento.medicion.horas-atribucion:24}")
    private int horasAtribucion;

    public record Resultado(int filasAyer, int filasHoy, int detallePurgado,
            int impresionesPurgadas) {
    }

    @Scheduled(fixedDelayString = "${descubrimiento.medicion.intervalo-ms:3600000}",
            initialDelayString = "${descubrimiento.medicion.retraso-inicial-ms:240000}")
    @Transactional
    public void programado() {
        Resultado r = agregarYPurgar();
        log.info("Medición: {} filas de ayer, {} de hoy; purgadas {} servidas y {} impresiones",
                r.filasAyer(), r.filasHoy(), r.detallePurgado(), r.impresionesPurgadas());

        indicadores.medicionTerminada(metricas.count());
    }

    /**
     * Agrega los dos últimos días y aplica la retención.
     *
     * <p>La purga estaba escrita desde la fase 1 y no la llamaba nadie: las
     * impresiones crecían sin límite. Ahora tienen fecha de caducidad, y el
     * agregado —que es lo que de verdad hace falta a largo plazo— sobrevive.
     */
    @Transactional
    public Resultado agregarYPurgar() {
        LocalDate hoy = LocalDate.now(ZoneOffset.UTC);
        LocalDate ayer = hoy.minusDays(1);

        int filasAyer = agregarDia(ayer);
        int filasHoy = agregarDia(hoy);

        Instant corte = Instant.now().minus(Duration.ofDays(pesos.getRetencionDias()));
        int servidasFuera = servidas.purgarAnterioresA(corte);
        int impresionesFuera = impresiones.purgarAnterioresA(corte);

        return new Resultado(filasAyer, filasHoy, servidasFuera, impresionesFuera);
    }

    /**
     * Un día completo, de medianoche a medianoche UTC.
     *
     * <p>Con transacción propia porque escribe: es público y se puede llamar
     * suelto para recalcular un día concreto, y sin esto esa llamada revienta
     * con «no active transaction». Cuando lo llama {@link #agregarYPurgar} se
     * une a la suya, que es lo correcto: los dos días y la purga son una sola
     * operación o no son ninguna.
     */
    @Transactional
    public int agregarDia(LocalDate dia) {
        Instant desde = dia.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant hasta = dia.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return servidas.agregarDia(dia, desde, hasta, horasAtribucion);
    }
}
