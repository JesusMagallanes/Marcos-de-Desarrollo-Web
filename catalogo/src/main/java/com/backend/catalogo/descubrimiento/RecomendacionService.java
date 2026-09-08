package com.backend.catalogo.descubrimiento;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;
import com.backend.catalogo.descubrimiento.dto.DescubrimientoDtos.Carrusel;
import com.backend.catalogo.producto.ProductoService;

import lombok.RequiredArgsConstructor;

/**
 * El motor: recupera, filtra, ordena y DIVERSIFICA.
 *
 * <p>Las cuatro etapas son deliberadas y en ese orden. Recuperar es barato y
 * trae de más; filtrar quita lo que no puede mostrarse; ordenar es caro y por
 * eso va sobre pocos; diversificar es lo último porque solo tiene sentido sobre
 * una lista ya ordenada.
 *
 * <p>Sin la última etapa el recomendador enseña cuatro monitores casi iguales:
 * todos puntúan alto por la misma razón, y esa lista es peor que una con la
 * mitad de precisión y cinco categorías distintas. En descubrimiento, la
 * diversidad no es un adorno del ranking, es parte del objetivo.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecomendacionService {

    /** Se recupera de más para que filtrar y diversificar tengan de dónde elegir. */
    private static final int FACTOR_SOBREMUESTREO = 5;

    /** Centinela para que `NOT IN (:lista)` nunca reciba una lista vacía. */
    private static final Long NINGUNO = -1L;

    private final CandidatoRepository candidatos;
    private final ItemDescartadoRepository descartes;
    private final ImpresionRepository impresiones;
    private final EventoInteraccionRepository eventos;
    private final PerfilService perfiles;
    private final TendenciaService tendencias;
    private final ProductoService productos;
    private final PesosDescubrimiento pesos;

    /**
     * El Home completo, adaptado a cuánto sabe el sistema de esta persona.
     *
     * <p>Un Home no puede ser el mismo para quien llega por primera vez que
     * para quien vuelve por décima: en el primer caso los carruseles personales
     * saldrían vacíos y la pantalla se vería rota. Se arma según el estado.
     *
     * <p>La de-duplicación es entre carruseles, no dentro de cada uno: un
     * producto aparece UNA vez en todo el Home. Los módulos se resuelven por
     * prioridad y cada uno retira lo que toma, así que el de arriba se queda
     * con lo mejor y los de abajo no lo repiten.
     */
    public List<Carrusel> home(UUID sujeto, String ubigeo, int porCarrusel) {
        Set<Long> yaUsados = new LinkedHashSet<>();
        List<Carrusel> salida = new ArrayList<>();

        boolean personalizable = perfiles.tienePerfil(sujeto);

        if (personalizable) {
            agregar(salida, segunIntereses(sujeto, porCarrusel, yaUsados), yaUsados);
        }

        agregar(salida, tendenciasDeZona(sujeto, ubigeo, porCarrusel, yaUsados), yaUsados);

        if (personalizable) {
            // El presupuesto de exploración va SIEMPRE, y etiquetado como lo
            // que es. Un desacierto se perdona en un carrusel que promete algo
            // distinto; el mismo desacierto dentro de «según tus intereses»
            // dice que el sistema no te conoce.
            agregar(salida, explorar(sujeto, porCarrusel, yaUsados), yaUsados);
        }

        agregar(salida, populares(porCarrusel, yaUsados), yaUsados);
        return salida;
    }

    /** SEGÚN TUS INTERESES · lo que sale del perfil, y de nada más. */
    public Carrusel segunIntereses(UUID sujeto, int limite, Set<Long> yaUsados) {
        List<Long> excluidos = excluidos(sujeto, yaUsados);
        List<Candidato> crudos = candidatos.segunIntereses(sujeto, pesos.getConfianzaK(),
                excluidos, limite * FACTOR_SOBREMUESTREO);

        List<PerfilFaceta> top = perfiles.top(sujeto, TipoFaceta.CATEGORIA, 1);
        String motivo = top.isEmpty()
                ? "Por lo que has estado explorando"
                : "Porque te interesa " + top.get(0).getId().getFaceta();

        return armar(ModuloDescubrimiento.SEGUN_TUS_INTERESES, null, motivo,
                diversificar(crudos, limite));
    }

    /** RELACIONADOS · por contenido: funciona con productos recién publicados. */
    public Carrusel similares(Long itemId, int limite) {
        List<Candidato> crudos = candidatos.similaresPorContenido(itemId,
                limite * FACTOR_SOBREMUESTREO);
        return armar(ModuloDescubrimiento.RELACIONADOS, null,
                "Comparte características con el que estás viendo",
                diversificar(crudos, limite));
    }

    /** LO MÁS VISTO EN TU ZONA · con degradación geográfica si falta gente. */
    public Carrusel tendenciasDeZona(UUID sujeto, String ubigeo, int limite, Set<Long> yaUsados) {
        TendenciaService.Resultado resultado = tendencias.enZona(ubigeo, limite * 2);

        List<Long> ids = new ArrayList<>(resultado.ids());
        ids.removeAll(excluidos(sujeto, yaUsados));

        if (ids.isEmpty()) {
            return null;
        }
        String detalle = switch (resultado.nivel()) {
            case DISTRITO, PROVINCIA, DEPARTAMENTO -> "tu zona";
            case NACIONAL -> "el Perú";
        };
        return new Carrusel(
                ModuloDescubrimiento.LO_MAS_VISTO_EN_TU_ZONA.name(),
                ModuloDescubrimiento.LO_MAS_VISTO_EN_TU_ZONA.titulo(detalle),
                ModuloDescubrimiento.LO_MAS_VISTO_EN_TU_ZONA.origen(),
                "Se está viendo mucho cerca de ti",
                productos.porIds(ids.subList(0, Math.min(limite, ids.size()))));
    }

    /** NUEVAS OPORTUNIDADES · categorías hermanas que todavía no ha pisado. */
    public Carrusel explorar(UUID sujeto, int limite, Set<Long> yaUsados) {
        List<Candidato> crudos = candidatos.paraExplorar(sujeto, excluidos(sujeto, yaUsados),
                limite * FACTOR_SOBREMUESTREO);
        return armar(ModuloDescubrimiento.DESCUBRE_ALGO_NUEVO, null,
                "Algo distinto de lo que sueles mirar", diversificar(crudos, limite));
    }

    /** ARRANQUE EN FRÍO · sin perfil no hay nada personal que ofrecer. */
    public Carrusel populares(int limite, Set<Long> yaUsados) {
        List<Long> excluidos = new ArrayList<>(yaUsados);
        excluidos.add(NINGUNO);
        List<Candidato> crudos = candidatos.populares(null, excluidos,
                limite * FACTOR_SOBREMUESTREO);
        return armar(ModuloDescubrimiento.POPULARES, null,
                "Bien valorado y con la ficha completa", diversificar(crudos, limite));
    }

    /* ══════════════ Etapa D · diversificación ══════════════ */

    /**
     * Recorta la lista respetando cuotas por categoría y por marca.
     *
     * <p>Es una versión ligera de MMR: en vez de calcular la similitud de cada
     * candidato con lo ya elegido —caro y difícil de explicar— se usan las dos
     * dimensiones que de verdad hacen que un carrusel se vea repetido. El
     * resultado es el mismo y se puede razonar mirándolo.
     *
     * <p>Si tras aplicar las cuotas faltan huecos, se rellenan con lo mejor que
     * quedó fuera: es preferible un carrusel lleno y algo repetitivo a uno con
     * tres tarjetas y un espacio vacío.
     */
    List<Long> diversificar(List<Candidato> ordenados, int limite) {
        Map<Long, Integer> porCategoria = new HashMap<>();
        Map<Long, Integer> porMarca = new HashMap<>();
        List<Long> elegidos = new ArrayList<>();
        List<Long> descartadosPorCuota = new ArrayList<>();

        for (Candidato c : ordenados) {
            if (elegidos.size() >= limite) {
                break;
            }
            int enCategoria = porCategoria.getOrDefault(c.getCategoriaId(), 0);
            int enMarca = c.getMarcaId() == null ? 0 : porMarca.getOrDefault(c.getMarcaId(), 0);

            if (enCategoria >= pesos.getMaximoPorCategoria()
                    || enMarca >= pesos.getMaximoPorMarca()) {
                descartadosPorCuota.add(c.getItemId());
                continue;
            }
            elegidos.add(c.getItemId());
            porCategoria.put(c.getCategoriaId(), enCategoria + 1);
            if (c.getMarcaId() != null) {
                porMarca.put(c.getMarcaId(), enMarca + 1);
            }
        }

        for (Long relleno : descartadosPorCuota) {
            if (elegidos.size() >= limite) {
                break;
            }
            elegidos.add(relleno);
        }
        return elegidos;
    }

    /* ══════════════ Etapa B · filtros duros ══════════════ */

    /**
     * Lo que NO puede aparecer: lo descartado a mano, lo que ya cansa y lo que
     * otro carrusel de esta misma pantalla ya se llevó.
     */
    private List<Long> excluidos(UUID sujeto, Set<Long> yaUsados) {
        Set<Long> fuera = new HashSet<>(yaUsados);
        fuera.addAll(descartes.idsDescartados(sujeto, TipoItem.PRODUCTO));
        fuera.addAll(impresiones.itemsConFatiga(sujeto, TipoItem.PRODUCTO.name(),
                Instant.now().minus(pesos.getDiasSupresionPorFatiga(), ChronoUnit.DAYS),
                pesos.getTopeImpresionesSinClic()));
        // `NOT IN ()` es un error de sintaxis, no una lista vacía.
        fuera.add(NINGUNO);
        return new ArrayList<>(fuera);
    }

    /** Lo último que miró, para «sigue donde lo dejaste». */
    public List<Long> vistosRecientemente(UUID sujeto, int limite) {
        return eventos.itemsVistosRecientemente(sujeto, TipoItem.PRODUCTO,
                PageRequest.of(0, limite));
    }

    private Carrusel armar(ModuloDescubrimiento modulo, String detalle, String motivo,
            List<Long> ids) {
        if (ids.isEmpty()) {
            return null;
        }
        return new Carrusel(modulo.name(), modulo.titulo(detalle), modulo.origen(), motivo,
                productos.porIds(ids));
    }

    private void agregar(List<Carrusel> salida, Carrusel carrusel, Set<Long> yaUsados) {
        if (carrusel == null || carrusel.items().isEmpty()) {
            return;
        }
        salida.add(carrusel);
        carrusel.items().forEach(p -> yaUsados.add(p.id()));
    }
}
