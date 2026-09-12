package com.backend.catalogo.descubrimiento;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Consolida las tendencias por zona y las sirve con degradación geográfica.
 *
 * <p>Calcularlas sobre el log de eventos en cada petición no escala: son
 * agregaciones sobre millones de filas para pintar un carrusel. Se consolidan
 * cada hora y el Home solo lee un índice.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class TendenciaService {

    private final TendenciaItemRepository tendencias;
    private final MetricasDescubrimiento metricas;
    private final CerrojoProceso cerrojo;
    private final PesosDescubrimiento pesos;

    /** Ventana que se considera «reciente» frente al periodo anterior. */
    @Value("${descubrimiento.tendencia.dias-ventana:7}")
    private int diasVentana;

    /**
     * Lo que se mueve en la zona del sujeto, subiendo de nivel si hace falta.
     *
     * <p>Ica no es Lima: en un distrito puede no haber gente suficiente para
     * que una tendencia signifique algo. En vez de devolver una lista vacía o,
     * peor, una sostenida por cuatro personas, se sube un peldaño: distrito →
     * provincia → departamento → nacional.
     *
     * <p>La degradación es silenciosa para quien mira, pero el nivel alcanzado
     * viaja en la respuesta para que la interfaz pueda titular con honestidad
     * «lo más visto en Ica» o «en el Perú» según corresponda.
     */
    public Resultado enZona(String ubigeo, int limite) {
        NivelGeografico nivel = ubigeo == null || ubigeo.isBlank()
                ? NivelGeografico.NACIONAL
                : NivelGeografico.DISTRITO;

        while (nivel != null) {
            List<TendenciaItem> filas = tendencias.masFuertes(nivel, nivel.zonaDe(ubigeo),
                    TipoItem.PRODUCTO, PageRequest.of(0, limite));
            if (!filas.isEmpty()) {
                return new Resultado(nivel, filas);
            }
            nivel = nivel.siguiente();
        }
        return new Resultado(NivelGeografico.NACIONAL, List.of());
    }

    /** Tendencias de una zona y el nivel del que salieron. */
    public record Resultado(NivelGeografico nivel, List<TendenciaItem> filas) {

        public List<Long> ids() {
            return filas.stream().map(t -> t.getId().getItemId()).toList();
        }
    }

    /**
     * Recalcula los cuatro niveles.
     *
     * <p>Cada hora y no en tiempo real porque una tendencia que cambia cada
     * minuto no es una tendencia; y porque el Home tiene que poder leerla sin
     * pagar la agregación.
     */
    @Scheduled(fixedDelayString = "${descubrimiento.tendencia.intervalo-ms:3600000}",
            initialDelayString = "${descubrimiento.tendencia.retraso-inicial-ms:120000}")
    @Transactional
    public void recalcular() {
        if (!cerrojo.intentar("tendencias")) {
            metricas.pasadaSaltada("tendencias");
            return;
        }
        metricas.pasada("tendencias", this::pasadaConCerrojo);
    }

    /** La pasada, ya con el cerrojo de esta transacción en la mano. */
    private void pasadaConCerrojo() {
        Instant ahora = Instant.now();
        Instant corte = ahora.minus(Duration.ofDays(diasVentana));
        // Se leen dos ventanas: la reciente y la anterior, para poder medir
        // crecimiento y no solo volumen.
        Instant desde = ahora.minus(Duration.ofDays(2L * diasVentana));

        for (NivelGeografico nivel : NivelGeografico.values()) {
            int filas = tendencias.recalcular(nivel.name(), nivel.digitos(), desde, corte,
                    pesos.getMinimoSujetos(), ahora);
            // Lo que no se recalculó en esta pasada dejó de ser tendencia.
            tendencias.purgarObsoletas(nivel, ahora);
            log.debug("Tendencias {} recalculadas: {} filas", nivel, filas);
        }
    }
}
