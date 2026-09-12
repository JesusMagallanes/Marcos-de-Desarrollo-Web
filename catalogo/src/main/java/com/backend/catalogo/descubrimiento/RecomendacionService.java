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

import com.backend.catalogo.descubrimiento.adaptativo.ContextoRanking;
import com.backend.catalogo.descubrimiento.adaptativo.RankerAdaptativo;
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

    /**
     * Cuántos ítems recientes se usan como punto de partida de la co-visita.
     *
     * <p>Pocos y a propósito. Lo que alguien miró hace tres semanas describe a
     * quien era, no a quien es; y cada semilla es un recorrido de índice más.
     */
    private static final int SEMILLAS_CO_VISITA = 12;

    private final CandidatoRepository candidatos;
    private final ItemDescartadoRepository descartes;
    private final ImpresionRepository impresiones;
    private final EventoInteraccionRepository eventos;
    private final PerfilService perfiles;
    private final TendenciaService tendencias;
    private final ProductoService productos;
    private final RankerHibrido ranker;
    private final RankerAdaptativo adaptativo;
    private final MetricasDescubrimiento metricas;
    private final RegistroRecomendacionService registro;
    private final ElegibilidadService elegibilidad;
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

        /*
         * El colaborativo va tras intereses y zona, y ANTES de exploracion.
         *
         * El orden importa porque cada modulo retira lo que se lleva: el de
         * arriba elige primero y los de abajo ya no lo repiten. Poner la
         * exploracion delante —como estuvo un rato— la dejaba quedarse con
         * candidatos que tenian conducta detras, para ofrecerlos como si fueran
         * una apuesta. Es al reves: primero lo que la gente sostiene con sus
         * actos, y lo especulativo rellena lo que quede.
         *
         * Va limitado igualmente. Es la senal mas llamativa que hay y si ocupara
         * la cabecera, el Home dejaria de responder «esto es lo tuyo» para
         * responder «esto es lo de otros», que es una tienda distinta y peor. El
         * tope vive en la configuracion porque solo se calibra con datos reales.
         */
        boolean huboColaborativo = false;
        if (personalizable && pesos.getMaximoModulosColaborativos() > 0) {
            Carrusel colab = colaborativo(sujeto, porCarrusel, yaUsados);
            huboColaborativo = colab != null;
            agregar(salida, colab, yaUsados);
        }

        if (personalizable) {
            // El presupuesto de exploración va SIEMPRE, y etiquetado como lo
            // que es. Un desacierto se perdona en un carrusel que promete algo
            // distinto; el mismo desacierto dentro de «según tus intereses»
            // dice que el sistema no te conoce.
            agregar(salida, explorar(sujeto, porCarrusel, yaUsados), yaUsados);
        }

        agregar(salida, populares(sujeto, porCarrusel, yaUsados), yaUsados);

        // La proporcion de Homes sin perfil es la medida de si el sistema esta
        // aprendiendo de la gente o repartiendo lo mismo a todo el mundo.
        metricas.homeServido(personalizable, huboColaborativo);
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

        List<Long> elegidos = diversificar(crudos, limite);
        anotar(sujeto, ModuloDescubrimiento.SEGUN_TUS_INTERESES, elegidos, crudos, true);

        return armar(ModuloDescubrimiento.SEGUN_TUS_INTERESES, null, motivo, elegidos);
    }

    /**
     * RELACIONADOS · dos maneras de parecerse, mezcladas.
     *
     * <p>El contenido responde «se parece a esto» y la co-visita responde «va
     * con esto», que no es lo mismo y a menudo es más útil: quien mira una
     * impresora no quiere otra impresora, quiere el tóner. El contenido, además,
     * es lo único que funciona con un producto recién publicado, que todavía no
     * ha coincidido con nadie.
     *
     * <p>Las dos entran al ranker híbrido, que las pone en la misma escala. Sin
     * eso ganaría siempre la de números más grandes, que aquí sería el contenido
     * por casualidad de sus unidades.
     */
    public Carrusel similares(Long itemId, UUID sujeto, int limite) {
        int aPedir = limite * FACTOR_SOBREMUESTREO;

        /*
         * Los filtros duros, que esta pantalla NO estaba aplicando.
         *
         * Es un fallo con cara visible: alguien marcaba «no me interesa» en un
         * producto y volvia a encontrarselo en la ficha de cualquier otro. El
         * boton parecia no servir, que es peor que no tenerlo. La fatiga estaba
         * igual de ausente.
         *
         * Sin sujeto no hay descartes que aplicar —no se sabe de quien serian—
         * y queda solo el propio producto, que no puede recomendarse a si mismo.
         */
        List<Long> excluidos = sujeto == null
                ? new ArrayList<>(List.of(itemId, NINGUNO))
                : excluidos(sujeto, Set.of(itemId));

        List<CandidatoConRazon> mezcla = new ArrayList<>();
        for (Candidato c : candidatos.similaresPorContenido(itemId, aPedir)) {
            mezcla.add(CandidatoConRazon.de(c, Origen.PERSONAL,
                    RazonRecomendacion.CONTENT_SIMILAR));
        }
        for (Candidato c : candidatos.porCoVisitaDeItem(itemId, excluidos, aPedir)) {
            mezcla.add(CandidatoConRazon.de(c, Origen.COHORTE,
                    RazonRecomendacion.CO_VIEWED));
        }

        /*
         * El contenido se filtra aqui y no en SQL a proposito: la consulta de
         * parecido por ficha no recibe exclusiones, y anadirselas obligaria a
         * tocar una consulta que funciona. La lista son unas decenas de
         * candidatos, asi que el filtro en memoria no cuesta nada.
         */
        Set<Long> fuera = new HashSet<>(excluidos);
        mezcla.removeIf(c -> fuera.contains(c.itemId()));

        boolean conPerfil = sujeto != null && perfiles.tienePerfil(sujeto);
        List<CandidatoConRazon> combinados = adaptativo.ordenar(mezcla,
                ContextoRanking.ficha(conPerfil), sujeto, evidenciaDe(sujeto));
        List<Long> elegidos = diversificar(new ArrayList<>(combinados), limite);

        /*
         * Se anota con la razon de CADA item, no con la del modulo. La ficha
         * mezcla parecido por contenido y co-visita, y aplastar las dos en
         * CONTENT_SIMILAR haria imposible saber cual de las dos acierta, que es
         * justo lo que la medicion existe para responder.
         */
        registro.anotar(sujeto, ModuloDescubrimiento.RELACIONADOS, elegidos,
                razonesDe(combinados), scoresDe(combinados), conPerfil);

        return armar(ModuloDescubrimiento.RELACIONADOS, null,
                "Relacionado con el que estás viendo", elegidos);
    }

    /**
     * COLABORATIVO · lo que descubrió gente con el mismo gusto.
     *
     * <p>Es la señal que la fase 1 no podía dar. Un perfil solo puede devolver
     * más de lo que ya contiene, porque está hecho exactamente de eso: quien
     * mire monitores verá monitores para siempre. Aquí el sistema puede
     * proponer algo que esta persona nunca ha mirado, y sostenerlo con algo
     * mejor que una corazonada: que a quienes se comportan como ella les
     * interesó.
     *
     * <p>Dos generadores en un solo carrusel. El de ítem a ítem parte de lo que
     * ella tocó; el de sujetos parecidos parte de quiénes se le parecen. Se
     * mezclan en el ranker en vez de ocupar un carrusel cada uno: dos módulos
     * colaborativos seguidos convertirían el Home en una máquina de «más de lo
     * mismo, pero de otros», que es justo lo que se quiere evitar.
     *
     * @return {@code null} si no hay evidencia suficiente; el Home lo omite
     */
    public Carrusel colaborativo(UUID sujeto, int limite, Set<Long> yaUsados) {
        if (sujeto == null) {
            return null;
        }
        List<Long> excluidos = excluidos(sujeto, yaUsados);
        int aPedir = limite * FACTOR_SOBREMUESTREO;
        Instant desde = Instant.now().minus(pesos.ventanaColaborativa());

        List<Candidato> porItem = candidatos.porCoVisita(
                sujeto, SEMILLAS_CO_VISITA, excluidos, aPedir);
        List<Candidato> porVecinos = candidatos.porSujetosSimilares(sujeto,
                pesos.getSimilitudMinima(), pesos.getSimilitudVecinosConsultados(), desde,
                pesos.getSimilitudMinAportantes(), excluidos, aPedir);

        metricas.candidatosGenerados(RazonRecomendacion.CO_VIEWED, porItem.size());
        metricas.candidatosGenerados(RazonRecomendacion.SIMILAR_SUBJECT, porVecinos.size());

        List<CandidatoConRazon> mezcla = new ArrayList<>();
        for (Candidato c : porItem) {
            mezcla.add(CandidatoConRazon.de(c, Origen.COHORTE, RazonRecomendacion.CO_VIEWED));
        }
        for (Candidato c : porVecinos) {
            mezcla.add(CandidatoConRazon.de(c, Origen.COHORTE,
                    RazonRecomendacion.SIMILAR_SUBJECT));
        }

        List<CandidatoConRazon> combinados = adaptativo.ordenar(mezcla,
                ContextoRanking.home(true), sujeto, evidenciaDe(sujeto));
        List<Long> elegidos = diversificar(new ArrayList<>(combinados), limite);

        /*
         * Este modulo es el unico que mezcla dos razones en un carrusel, asi que
         * es el unico donde anotar «la razon del modulo» seria mentira. Se pasa
         * la de cada item, que es justo el dato que permitira saber cual de los
         * dos generadores acierta.
         */
        registro.anotar(sujeto, ModuloDescubrimiento.OTROS_DESCUBRIERON, elegidos,
                razonesDe(combinados), scoresDe(combinados), true);

        return armar(ModuloDescubrimiento.OTROS_DESCUBRIERON, null,
                "Descubierto por gente con intereses parecidos a los tuyos", elegidos);
    }

    /**
     * Cuánta evidencia sostiene el perfil de este sujeto.
     *
     * <p>Decide cuánto se explora: a quien el sistema no conoce, explorar es lo
     * único que puede hacer; a quien conoce bien, explorar cuesta y se hace con
     * medida. Se usa el número de eventos de su faceta más fuerte, que es la
     * cifra que ya tenía la fase 1 y no obliga a preguntar nada nuevo.
     */
    private int evidenciaDe(UUID sujeto) {
        if (sujeto == null) {
            return 0;
        }
        List<PerfilFaceta> top = perfiles.top(sujeto, TipoFaceta.CATEGORIA, 1);
        return top.isEmpty() ? 0 : top.get(0).getEventos();
    }

    /**
     * Reserva los últimos huecos para lo recién llegado, si no entró por score.
     *
     * <h4>Por qué un techo no bastaba</h4>
     *
     * <p>La primera versión se limitaba a traer como mucho {@code cupo}
     * candidatos nuevos y dejar que compitieran. No funcionaba, y las pruebas lo
     * dijeron: con la normalización por origen, el mejor producto nuevo queda en
     * {@code 1,0 × peso(EXPLORACION)} —0,5 en el Home de quien no tiene perfil—
     * mientras que entre sesenta candidatos populares hay decenas por encima de
     * eso. En doce huecos no entraba nunca. El cupo acotaba algo que no pasaba,
     * y el mecanismo entero era decorativo.
     *
     * <p>Un presupuesto de exposición ES una reserva; si no, no es un
     * presupuesto. Es además lo que ya hace la exploración de la fase 4, que
     * aparta posiciones por novedad en vez de confiar en que el score las gane.
     *
     * <h4>Por qué esto NO es saltarse el ranker</h4>
     *
     * <p>Los candidatos nuevos llegaron aquí por la consulta que aplica los
     * mismos filtros duros que todas —moderación, stock, exclusiones, descarte,
     * fatiga— y pasaron por el ranker, que los puntuó y los ordenó ENTRE ELLOS.
     * Lo único que hace esta reserva es garantizar que los mejor puntuados de
     * ese grupo ocupen unos pocos huecos. No resucita nada ni reordena a nadie
     * más.
     *
     * <p>Van al final de la lista: la primera tarjeta es la que más se mira y no
     * es sitio para una apuesta. Y nunca pueden ser todas — se sustituyen como
     * mucho {@code cupo} posiciones de una lista que siempre conserva las de
     * arriba.
     */
    private List<Long> reservarCupoDeNovedad(List<Long> elegidos,
            List<CandidatoConRazon> combinados) {

        int cupo = pesos.getCatalogoNuevoCupo();
        if (cupo <= 0 || elegidos.size() <= 1) {
            return elegidos;
        }

        List<Long> novedades = combinados.stream()
                .filter(c -> c.razon() == RazonRecomendacion.NEW_ARRIVAL)
                .map(CandidatoConRazon::itemId)
                .toList();
        if (novedades.isEmpty()) {
            // Sin nada nuevo, el carrusel es exactamente el de siempre.
            return elegidos;
        }

        List<Long> salida = new ArrayList<>(elegidos);
        long yaDentro = salida.stream().filter(novedades::contains).count();

        // Nunca mas de `cupo`, y nunca mas de lo que deje media lista en pie.
        int porColocar = (int) Math.min(cupo - yaDentro, (long) (salida.size() - 1) / 2);

        for (Long nueva : novedades) {
            if (porColocar <= 0) {
                break;
            }
            if (salida.contains(nueva)) {
                continue;
            }
            salida.set(salida.size() - porColocar, nueva);
            porColocar--;
        }
        return salida;
    }

    /** {@code itemId -> razon}, para anotar lo servido sin perder de dónde vino. */
    private Map<Long, RazonRecomendacion> razonesDe(List<CandidatoConRazon> candidatos) {
        Map<Long, RazonRecomendacion> razones = new HashMap<>();
        candidatos.forEach(c -> razones.put(c.itemId(), c.razon()));
        return razones;
    }

    /** {@code itemId -> score final}, el que produjo el orden. */
    private Map<Long, Double> scoresDe(List<CandidatoConRazon> candidatos) {
        Map<Long, Double> scores = new HashMap<>();
        candidatos.forEach(c -> scores.put(c.itemId(), c.score()));
        return scores;
    }

    /**
     * Anota un carrusel cuyo módulo determina la razón, que son casi todos.
     *
     * <p>El score se aporta cuando el generador produjo uno comparable. Los que
     * no —la tendencia de zona llega ya ordenada por el proceso por lotes— no
     * lo inventan: un cero es honesto y un número improvisado acabaría
     * comparándose con los de verdad.
     */
    private void anotar(UUID sujeto, ModuloDescubrimiento modulo, List<Long> ids,
            List<Candidato> crudos, boolean conPerfil) {
        Map<Long, Double> scores = new HashMap<>();
        for (Candidato c : crudos) {
            if (c.getScore() != null) {
                scores.put(c.getItemId(), c.getScore());
            }
        }
        registro.anotar(sujeto, modulo, ids, Map.of(), scores, conPerfil);
    }

    /** LO MÁS VISTO EN TU ZONA · con degradación geográfica si falta gente. */
    public Carrusel tendenciasDeZona(UUID sujeto, String ubigeo, int limite, Set<Long> yaUsados) {
        TendenciaService.Resultado resultado = tendencias.enZona(ubigeo, limite * 2);

        /*
         * ELEGIBILIDAD ANTES DE NADA, y aqui es donde de verdad hacia falta.
         *
         * Los otros seis generadores comprueban moderacion y stock dentro de su
         * propio SQL, asi que lo que devuelven ya es elegible. Este no: lee
         * IDENTIFICADORES de `tendencia_item`, una tabla derivada que se
         * recalcula cada hora. Un producto que se agota a las 10:05 seguia
         * saliendo en «lo mas visto en tu zona» hasta las 11:00, y quien pulsaba
         * se encontraba una ficha sin stock.
         *
         * Va antes de las exclusiones y antes del recorte, no despues: filtrar
         * al final dejaria huecos en el carrusel y —lo importante— convertiria
         * la elegibilidad en algo que el ranking podria llegar a saltarse.
         */
        List<Long> ids = new ArrayList<>(elegibilidad.filtrar(resultado.ids()));
        ids.removeAll(excluidos(sujeto, yaUsados));

        if (ids.isEmpty()) {
            return null;
        }
        String detalle = switch (resultado.nivel()) {
            case DISTRITO, PROVINCIA, DEPARTAMENTO -> "tu zona";
            case NACIONAL -> "el Perú";
        };
        List<Long> elegidos = ids.subList(0, Math.min(limite, ids.size()));
        /*
         * Sin score: este modulo no ordena, lee un orden que ya calculo el
         * proceso de tendencias. Anotar un numero improvisado seria peor que no
         * anotar ninguno, porque acabaria comparandose con los de verdad.
         */
        anotar(sujeto, ModuloDescubrimiento.LO_MAS_VISTO_EN_TU_ZONA, elegidos,
                List.of(), sujeto != null && perfiles.tienePerfil(sujeto));

        return new Carrusel(
                ModuloDescubrimiento.LO_MAS_VISTO_EN_TU_ZONA.name(),
                ModuloDescubrimiento.LO_MAS_VISTO_EN_TU_ZONA.titulo(detalle),
                ModuloDescubrimiento.LO_MAS_VISTO_EN_TU_ZONA.origen(),
                "Se está viendo mucho cerca de ti",
                productos.porIds(elegidos));
    }

    /** NUEVAS OPORTUNIDADES · categorías hermanas que todavía no ha pisado. */
    public Carrusel explorar(UUID sujeto, int limite, Set<Long> yaUsados) {
        List<Candidato> crudos = candidatos.paraExplorar(sujeto, excluidos(sujeto, yaUsados),
                limite * FACTOR_SOBREMUESTREO);
        List<Long> elegidos = diversificar(crudos, limite);
        anotar(sujeto, ModuloDescubrimiento.DESCUBRE_ALGO_NUEVO, elegidos, crudos, true);

        return armar(ModuloDescubrimiento.DESCUBRE_ALGO_NUEVO, null,
                "Algo distinto de lo que sueles mirar", elegidos);
    }

    /**
     * ARRANQUE EN FRÍO · sin perfil no hay nada personal que ofrecer.
     *
     * <p>Nace para quien no tiene rastro, pero se anade al Home SIEMPRE, tambien
     * al de quien si lo tiene: es el relleno que evita una pantalla a medias. Por
     * eso necesita el sujeto. Sin el se saltaba los filtros duros, y lo que se
     * colaba era justo lo peor que puede colarse: un producto que la persona
     * acababa de marcar como «no me interesa» reaparecia en la siguiente carga.
     * Descartar algo y verlo volver es peor que no poder descartarlo.
     */
    public Carrusel populares(UUID sujeto, int limite, Set<Long> yaUsados) {
        List<Long> excluidos;
        if (sujeto == null) {
            excluidos = new ArrayList<>(yaUsados);
            excluidos.add(NINGUNO);
        } else {
            excluidos = excluidos(sujeto, yaUsados);
        }
        /*
         * CATALOGO NUEVO · el unico sitio donde un producto sin historial puede
         * asomar.
         *
         * Va aqui y no en un modulo propio por una razon de alcance: este
         * carrusel se sirve SIEMPRE, tambien a quien acaba de llegar y no tiene
         * perfil, que es precisamente quien nunca veria un producto nuevo por
         * ninguna otra via. Los modulos personalizados no le llegan.
         *
         * La consulta trae como mucho `catalogoNuevoCupo` candidatos, con los
         * MISMOS filtros que el resto —moderacion, stock, exclusiones—. Ser
         * nuevo no es una credencial: un producto recien dado de alta y ya
         * agotado no entra, y uno descartado no vuelve por ser reciente.
         */
        List<Candidato> nuevos = candidatos.catalogoNuevo(
                pesos.fronteraCatalogoNuevo(), excluidos, pesos.getCatalogoNuevoCupo());
        metricas.candidatosGenerados(RazonRecomendacion.NEW_ARRIVAL, nuevos.size());

        /*
         * Lo nuevo se pide PRIMERO y se excluye de la otra consulta.
         *
         * Sin esto, un producto recien llegado con ficha completa lo devuelven
         * las dos: la de novedad y la de populares —cumple moderacion y stock—.
         * El ranker deduplica quedandose con el mejor score, que es el de
         * TENDENCIA por tener mas peso, y el candidato perdia su razon
         * NEW_ARRIVAL. Con ella se perdian las dos cosas que justifican esta
         * puerta: poder MEDIR si sirve, y poder reservarle sitio.
         *
         * Lo delato una prueba que exigia que la novedad llegara a servirse.
         */
        List<Long> sinRepetir = new ArrayList<>(excluidos);
        nuevos.forEach(c -> sinRepetir.add(c.getItemId()));

        List<Candidato> crudos = candidatos.populares(null, sinRepetir,
                limite * FACTOR_SOBREMUESTREO);

        List<CandidatoConRazon> mezcla = new ArrayList<>();
        for (Candidato c : crudos) {
            mezcla.add(CandidatoConRazon.de(c, Origen.TENDENCIA, RazonRecomendacion.POPULAR));
        }
        for (Candidato c : nuevos) {
            mezcla.add(CandidatoConRazon.de(c, Origen.EXPLORACION,
                    RazonRecomendacion.NEW_ARRIVAL));
        }

        /*
         * Y ahora TODO pasa por el ranker, lo nuevo y lo popular juntos.
         *
         * Este modulo no lo usaba: ordenaba por el score de su SQL y
         * diversificaba. Ahora tiene que hacerlo, porque si no el catalogo nuevo
         * entraria por una via que se salta la ponderacion por contexto — que es
         * exactamente el bypass que no puede existir. Con `EXPLORACION` frente a
         * `TENDENCIA`, un producto nuevo pesa menos que uno asentado y compite;
         * puede perfectamente no aparecer, y eso es lo correcto.
         */
        boolean conPerfilAqui = sujeto != null && perfiles.tienePerfil(sujeto);
        List<CandidatoConRazon> combinados = adaptativo.ordenar(mezcla,
                ContextoRanking.home(conPerfilAqui), sujeto, evidenciaDe(sujeto));
        List<Long> elegidos = reservarCupoDeNovedad(
                diversificar(new ArrayList<>(combinados), limite), combinados);
        /*
         * `conPerfil` es «habia algo personal de donde tirar», NO «habia sujeto».
         *
         * Estaba mal: se anotaba `sujeto != null`, con lo que un visitante recien
         * llegado —que tiene identificador desde su primera peticion pero no
         * tiene perfil— quedaba registrado como si el sistema lo conociera. Eso
         * vaciaba de sentido la segmentacion de arranque en frio, que es
         * justamente la que dice si el recomendador sirve a quien llega o solo a
         * quien ya lo usaba. Lo delato el recorrido en navegador: el sujeto
         * estrenado tras cerrar sesion salia con perfil.
         */
        registro.anotar(sujeto, ModuloDescubrimiento.POPULARES, elegidos,
                razonesDe(combinados), scoresDe(combinados), conPerfilAqui);

        return armar(ModuloDescubrimiento.POPULARES, null,
                "Bien valorado y con la ficha completa", elegidos);
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
