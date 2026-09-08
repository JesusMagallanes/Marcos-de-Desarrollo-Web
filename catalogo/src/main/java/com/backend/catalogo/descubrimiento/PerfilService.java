package com.backend.catalogo.descubrimiento;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;

import lombok.RequiredArgsConstructor;

/**
 * Convierte un evento en interés.
 *
 * <p>Todo lo que escribe aplica el olvido en el momento:
 * {@code score = score × 2^(−Δt/vidaMedia) + peso}. Es una sentencia por
 * faceta, en tiempo constante. El diseño obvio —recalcular el perfil leyendo
 * los eventos— es el que no aguanta, porque el coste crece con el historial de
 * cada persona.
 */
@Service
@RequiredArgsConstructor
public class PerfilService {

    private final PerfilFacetaRepository perfiles;
    private final PesosDescubrimiento pesos;

    /**
     * Aplica un evento al perfil del sujeto.
     *
     * @param repeticion cuántas veces ya hizo esto mismo en la sesión
     */
    @Transactional
    public void aplicar(UUID sujeto, TipoEvento tipo, Long productoId, Long categoriaId,
            Integer dwellMs, int repeticion) {

        double peso = pesos.pesoDe(tipo)
                * pesos.factorSaturacion(repeticion)
                * pesos.factorDwell(dwellMs);

        if (peso == 0.0) {
            return;
        }

        long vidaMediaSeg = pesos.getVidaMedia().toSeconds();

        if (categoriaId != null) {
            perfiles.acumularCategoriaConAncestros(sujeto, categoriaId, peso,
                    pesos.getAtenuacionPorNivel(), vidaMediaSeg);
        }

        /*
         * Del producto salen dos facetas más: su marca y sus características.
         *
         * Es de donde el sistema aprende que a alguien le interesan las 27
         * pulgadas y los 144 Hz sin que lo haya dicho nunca. Solo se hace con
         * señales de interés real: una impresión sin clic o un descarte no
         * deben enseñar preferencias de atributo, solo restar en su categoría.
         */
        if (productoId != null && peso > 0) {
            perfiles.acumularMarcaDe(sujeto, productoId, peso * 0.6, vidaMediaSeg);
            perfiles.acumularAtributosDe(sujeto, productoId, peso * 0.4, vidaMediaSeg);
        }
    }

    /**
     * Aplica un filtro por característica: la señal más específica que hay.
     *
     * <p>«Pulgadas ≥ 27» dice el atributo Y el valor exactos, sin ambigüedad.
     * Solo es aprovechable porque los atributos son filas en
     * {@code producto_atributo}; con las especificaciones en un bloque de texto
     * no habría nada que registrar.
     */
    @Transactional
    public void aplicarFiltro(UUID sujeto, String codigoAtributo, String valor) {
        if (codigoAtributo == null || codigoAtributo.isBlank() || valor == null) {
            return;
        }
        perfiles.acumular(sujeto, TipoFaceta.ATRIBUTO.name(),
                codigoAtributo + "=" + valor,
                pesos.pesoDe(TipoEvento.ATTRIBUTE_FILTER),
                pesos.getVidaMedia().toSeconds());
    }

    @Transactional(readOnly = true)
    public List<PerfilFaceta> top(UUID sujeto, TipoFaceta tipo, int limite) {
        return perfiles.top(sujeto, tipo, PageRequest.of(0, limite));
    }

    @Transactional(readOnly = true)
    public List<PerfilFaceta> completo(UUID sujeto) {
        return perfiles.todasDe(sujeto);
    }

    /**
     * Borra el perfil de intereses del sujeto.
     *
     * <p>Derecho de cancelacion de la Ley 29733. Se van las facetas, que es lo
     * que define a la persona; el log de eventos se purga solo por retencion y
     * no aqui, porque borrarlo al instante dejaria las metricas agregadas
     * inconsistentes sin que eso proteja a nadie mas.
     */
    @Transactional
    public void olvidar(UUID sujeto) {
        perfiles.deleteAll(perfiles.todasDe(sujeto));
    }

    /** Si el sujeto tiene suficiente historia para personalizar de verdad. */
    @Transactional(readOnly = true)
    public boolean tienePerfil(UUID sujeto) {
        return perfiles.countByIdSujetoId(sujeto) > 0;
    }
}
