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

    /**
     * Todo lo que el Home necesita saber del perfil, resuelto de una vez.
     *
     * <h4>Por qué existe</h4>
     *
     * <p>Porque lo mismo se preguntaba ocho veces por peticion. Armar un Home
     * son seis modulos y cada uno resolvia por su cuenta si habia perfil y
     * cuanta evidencia lo sostenia: {@code tienePerfil} en cuatro sitios,
     * {@code top} en otros cuatro a traves de {@code evidenciaDe}. Ocho
     * consultas para dos datos que no cambian durante la peticion.
     *
     * <p>No era un N+1 por candidato —que es lo que vigilaban las pruebas de
     * las fases anteriores— sino repeticion por modulo, que ninguna de ellas
     * podia ver. Lo encontro la auditoria de latencia.
     *
     * <h4>Por qué siguen siendo DOS consultas y no una</h4>
     *
     * <p>Porque preguntan cosas distintas y colapsarlas cambiaria el
     * comportamiento. {@code tienePerfil} cuenta facetas de CUALQUIER tipo;
     * {@code top} pide solo las de categoria. Alguien que solo haya dejado
     * rastro de marcas o de atributos tiene perfil y no tiene categoria
     * preferida, y deducir lo primero de lo segundo lo dejaria fuera de los
     * modulos personalizados.
     */
    @Transactional(readOnly = true)
    public EstadoDePerfil estado(UUID sujeto) {
        if (sujeto == null) {
            return EstadoDePerfil.sinNada();
        }
        List<PerfilFaceta> top = top(sujeto, TipoFaceta.CATEGORIA, 1);
        return new EstadoDePerfil(
                tienePerfil(sujeto),
                top.isEmpty() ? 0 : top.get(0).getEventos(),
                top.isEmpty() ? null : top.get(0).getId().getFaceta());
    }

    /**
     * El perfil del sujeto visto desde el armado del Home.
     *
     * @param tiene si hay alguna faceta, del tipo que sea. Es lo que decide si
     *     se personaliza y lo que se anota como arranque en frio
     * @param eventos cuanta evidencia sostiene su categoria principal; calibra
     *     el presupuesto de exploracion
     * @param facetaPrincipal el nombre de esa categoria, para el texto del
     *     motivo. {@code null} si no hay ninguna
     */
    public record EstadoDePerfil(boolean tiene, int eventos, String facetaPrincipal) {

        public static EstadoDePerfil sinNada() {
            return new EstadoDePerfil(false, 0, null);
        }
    }
}
