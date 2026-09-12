package com.backend.catalogo.descubrimiento;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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

import com.backend.catalogo.descubrimiento.CooldownService.Cooldown;
import com.backend.catalogo.descubrimiento.PerfilService.EstadoDePerfil;
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

    /** Las dos superficies que sirve este servicio, para etiquetar los tiempos. */
    private static final String HOME_S = MetricasPipeline.HOME;
    private static final String FICHA_S = MetricasPipeline.FICHA;

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
    private final EventoInteraccionRepository eventos;
    private final PerfilService perfiles;
    private final TendenciaService tendencias;
    private final ProductoService productos;
    private final RankerHibrido ranker;
    private final RankerAdaptativo adaptativo;
    private final MetricasDescubrimiento metricas;
    private final RegistroRecomendacionService registro;
    private final ElegibilidadService elegibilidad;
    private final SesionService sesiones;
    private final CooldownService cooldowns;
    private final MetricasPipeline cronometros;
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
        return cronometros.total(MetricasPipeline.HOME,
                () -> armarHome(sujeto, ubigeo, porCarrusel));
    }

    /**
     * El Home de verdad, con el cronómetro total ya puesto por fuera.
     *
     * <p>El total se mide aquí y no sumando etapas: sumar solo puede devolver lo
     * que se instrumentó, y lo que interesa saber es justamente si queda tiempo
     * fuera de las etapas conocidas.
     */
    private List<Carrusel> armarHome(UUID sujeto, String ubigeo, int porCarrusel) {
        Set<Long> yaUsados = new LinkedHashSet<>();
        List<Carrusel> salida = new ArrayList<>();

        /*
         * EL PERFIL, UNA SOLA VEZ.
         *
         * Se preguntaba ocho veces por peticion: cada uno de los seis modulos
         * resolvia por su cuenta si habia perfil y cuanta evidencia lo sostenia.
         * No era un N+1 por candidato —lo que vigilaban las pruebas anteriores—
         * sino repeticion por modulo, que ninguna de ellas podia ver; lo
         * encontro la auditoria de latencia de este bloque.
         *
         * Son dos consultas y no una porque preguntan cosas distintas: si hay
         * alguna faceta, del tipo que sea, y cual es la categoria principal.
         * Colapsarlas dejaria fuera de lo personalizado a quien solo haya dejado
         * rastro de marcas.
         */
        EstadoDePerfil perfil = cronometros.comun(MetricasPipeline.HOME,
                MetricasPipeline.PERFIL, () -> perfiles.estado(sujeto));
        boolean conPerfil = perfil.tiene();

        /*
         * Con perfil O con sesion. El «o» es lo que anade este bloque.
         *
         * Antes hacia falta perfil para recibir cualquier cosa personalizada, y
         * eso dejaba fuera al caso mas obvio: alguien que acaba de llegar, busca
         * «monitor», abre dos fichas y sigue viendo lo mismo que veria si no
         * hubiera hecho nada. Ha dicho de sobra lo que quiere; esperar a tener
         * perfil para escucharle es desperdiciar la unica informacion que hay.
         *
         * La intencion exige un minimo de interacciones, asi que un clic suelto
         * no dispara esto: sin senal suficiente el Home sigue siendo el de
         * tendencia, zona y exploracion.
         */
        SesionService.Intencion intencion = sesiones.intencionDe(sujeto);
        boolean personalizable = conPerfil || intencion.hayIntencion();

        /*
         * El enfriamiento, UNA vez para toda la pantalla.
         *
         * Es lo que hace que sea una consulta por peticion y no una por
         * carrusel, y —mas importante— que los seis modulos lean exactamente el
         * mismo estado. Calcularlo dentro de cada uno abriria la puerta a que
         * un producto quedara enfriado en el primero y no en el cuarto porque
         * entre medias entro una impresion, que es una incoherencia dificil de
         * ver y imposible de reproducir.
         */
        Cooldown enfriamiento = cooldowns.de(sujeto);

        if (personalizable) {
            agregar(salida, segunIntereses(sujeto, porCarrusel, yaUsados, intencion,
                    enfriamiento, perfil), yaUsados);
        }

        agregar(salida, tendenciasDeZona(sujeto, ubigeo, porCarrusel, yaUsados, enfriamiento,
                perfil), yaUsados);

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
            Carrusel colab = colaborativo(sujeto, porCarrusel, yaUsados, enfriamiento,
                    perfil);
            huboColaborativo = colab != null;
            agregar(salida, colab, yaUsados);
        }

        if (personalizable) {
            // El presupuesto de exploración va SIEMPRE, y etiquetado como lo
            // que es. Un desacierto se perdona en un carrusel que promete algo
            // distinto; el mismo desacierto dentro de «según tus intereses»
            // dice que el sistema no te conoce.
            agregar(salida, explorar(sujeto, porCarrusel, yaUsados, enfriamiento),
                    yaUsados);
        }

        agregar(salida, populares(sujeto, porCarrusel, yaUsados, enfriamiento, perfil),
                yaUsados);

        // La proporcion de Homes sin perfil es la medida de si el sistema esta
        // aprendiendo de la gente o repartiendo lo mismo a todo el mundo.
        /*
         * `conPerfil` y NO `personalizable`. La diferencia importa: desde este
         * bloque se personaliza tambien con solo sesion, y contar eso como
         * «tenia perfil» volveria a vaciar de sentido la segmentacion de
         * arranque en frio — el mismo fallo que costo una vuelta en la fase 4.
         */
        metricas.homeServido(conPerfil, huboColaborativo);
        return salida;
    }

    /**
     * SEGÚN TUS INTERESES · el perfil, y lo que está mirando ahora.
     *
     * <p>Dos señales que pueden contradecirse, y tienen que poder hacerlo. El
     * perfil dice lo que le gusta a alguien y acierta; la sesión dice a qué ha
     * venido hoy. A quien le gustan los monitores pero entró buscando una
     * impresora, servirle monitores es tener razón y no ayudar.
     *
     * <p>La sesión NO sustituye al perfil: se suma como candidatos más, y el
     * ranker decide. Un perfil de meses no puede deshacerse por una visita, ni
     * una visita puede quedar enterrada bajo meses de otra cosa.
     *
     * <p>Y es lo único que hay para quien todavía no tiene perfil. Ver
     * {@code home()}: este módulo ahora se sirve también cuando solo hay sesión.
     */
    public Carrusel segunIntereses(UUID sujeto, int limite, Set<Long> yaUsados) {
        return segunIntereses(sujeto, limite, yaUsados, sesiones.intencionDe(sujeto),
                cooldowns.de(sujeto), perfiles.estado(sujeto));
    }

    /**
     * La misma, con la intención ya calculada.
     *
     * <p>El Home la necesita antes —para decidir si sirve este módulo— y
     * recalcularla aquí serían dos consultas más por petición para leer
     * exactamente lo mismo.
     */
    Carrusel segunIntereses(UUID sujeto, int limite, Set<Long> yaUsados,
            SesionService.Intencion intencion, Cooldown enfriamiento, EstadoDePerfil perfil) {
        ModuloDescubrimiento modulo = ModuloDescubrimiento.SEGUN_TUS_INTERESES;
        String nombre = modulo.name();

        List<Long> excluidos = cronometros.etapa(HOME_S, nombre, MetricasPipeline.EXCLUSIONES,
                () -> excluidos(sujeto, yaUsados, modulo, enfriamiento));
        int aPedir = limite * FACTOR_SOBREMUESTREO;

        List<Candidato> crudos = cronometros.etapa(HOME_S, nombre,
                MetricasPipeline.CANDIDATOS_SQL,
                () -> candidatos.segunIntereses(sujeto, pesos.getConfianzaK(),
                        excluidos, aPedir));
        cronometros.candidatos(HOME_S, nombre, crudos.size());

        List<CandidatoConRazon> mezcla = new ArrayList<>();
        for (Candidato c : crudos) {
            mezcla.add(CandidatoConRazon.de(c, Origen.PERSONAL,
                    RazonRecomendacion.PERSONAL_INTEREST));
        }

        /*
         * La intención de sesión, si la hay. Se piden los candidatos APARTE y
         * excluyendo los que ya trajo el perfil: si un producto llegara por las
         * dos vías, el ranker deduplicaria quedandose con el mejor score y se
         * perderia saber cual de las dos senales lo trajo — el mismo fallo que
         * costo una vuelta en el bloque B.
         */
        List<Long> deSesion = intencion.categoriasDeSesion(pesos.getSesionMaximasFacetas());

        if (!deSesion.isEmpty()) {
            List<Long> sinRepetir = new ArrayList<>(excluidos);
            crudos.forEach(c -> sinRepetir.add(c.getItemId()));

            List<Candidato> porSesion = cronometros.etapa(HOME_S, nombre,
                    MetricasPipeline.CANDIDATOS_SQL,
                    () -> candidatos.porIntencionDeSesion(deSesion, sinRepetir, aPedir));
            for (Candidato c : porSesion) {
                mezcla.add(CandidatoConRazon.de(c, Origen.PERSONAL,
                        RazonRecomendacion.SESSION_INTENT));
            }
            metricas.candidatosGenerados(RazonRecomendacion.SESSION_INTENT, porSesion.size());
        }

        cronometros.etapa(HOME_S, nombre, MetricasPipeline.ENFRIAR,
                () -> enfriar(mezcla, modulo, enfriamiento));

        // El motivo sale del perfil ya resuelto: ni una consulta mas.
        String motivo = perfil.facetaPrincipal() == null
                ? "Por lo que has estado explorando"
                : "Porque te interesa " + perfil.facetaPrincipal();

        List<CandidatoConRazon> combinados = cronometros.etapa(HOME_S, nombre,
                MetricasPipeline.ORDENAR,
                () -> adaptativo.ordenar(mezcla, ContextoRanking.home(perfil.tiene()),
                        sujeto, perfil.eventos()));
        List<Long> elegidos = cronometros.etapa(HOME_S, nombre, MetricasPipeline.DIVERSIFICAR,
                () -> diversificar(new ArrayList<>(combinados), limite));

        cronometros.etapa(HOME_S, nombre, MetricasPipeline.ANOTAR,
                () -> registro.anotar(sujeto, modulo, elegidos,
                        razonesDe(combinados), scoresDe(combinados), perfil.tiene()));

        return armar(HOME_S, modulo, null, motivo, elegidos);
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
        return cronometros.total(FICHA_S, () -> armarFicha(itemId, sujeto, limite));
    }

    /** La ficha de verdad, con el total ya puesto por fuera. */
    private Carrusel armarFicha(Long itemId, UUID sujeto, int limite) {
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
        ModuloDescubrimiento modulo = ModuloDescubrimiento.RELACIONADOS;
        String nombre = modulo.name();
        Cooldown enfriamiento = cooldowns.de(sujeto);

        List<Long> excluidos = cronometros.etapa(FICHA_S, nombre,
                MetricasPipeline.EXCLUSIONES,
                () -> sujeto == null
                        ? new ArrayList<>(List.of(itemId, NINGUNO))
                        : excluidos(sujeto, Set.of(itemId), modulo, enfriamiento));

        /*
         * Los dos generadores se cronometran por separado. Responden preguntas
         * distintas —«se parece a esto» y «va con esto»— y cuestan cosas
         * distintas: el de contenido cruza atributos y el de co-visita lee una
         * tabla precalculada. Un solo numero no diria cual conviene tocar.
         */
        List<Candidato> porContenido = cronometros.etapa(FICHA_S, nombre,
                MetricasPipeline.CONTENIDO,
                () -> candidatos.similaresPorContenido(itemId, aPedir));
        List<Candidato> porCoVisita = cronometros.etapa(FICHA_S, nombre,
                MetricasPipeline.CO_VISITA,
                () -> candidatos.porCoVisitaDeItem(itemId, excluidos, aPedir));
        cronometros.candidatos(FICHA_S, nombre, porContenido.size() + porCoVisita.size());

        List<CandidatoConRazon> mezcla = new ArrayList<>();
        for (Candidato c : porContenido) {
            mezcla.add(CandidatoConRazon.de(c, Origen.PERSONAL,
                    RazonRecomendacion.CONTENT_SIMILAR));
        }
        for (Candidato c : porCoVisita) {
            mezcla.add(CandidatoConRazon.de(c, Origen.COHORTE,
                    RazonRecomendacion.CO_VIEWED));
        }

        /*
         * El contenido se filtra aqui y no en SQL a proposito: la consulta de
         * parecido por ficha no recibe exclusiones, y anadirselas obligaria a
         * tocar una consulta que funciona. La lista son unas decenas de
         * candidatos, asi que el filtro en memoria no cuesta nada.
         */
        cronometros.etapa(FICHA_S, nombre, MetricasPipeline.FILTRO, () -> {
            Set<Long> fuera = new HashSet<>(excluidos);
            mezcla.removeIf(c -> fuera.contains(c.itemId()));
        });
        cronometros.etapa(FICHA_S, nombre, MetricasPipeline.ENFRIAR,
                () -> enfriar(mezcla, modulo, enfriamiento));

        // El perfil, una vez tambien aqui: antes eran dos consultas.
        EstadoDePerfil perfil = cronometros.comun(FICHA_S, MetricasPipeline.PERFIL,
                () -> perfiles.estado(sujeto));
        boolean conPerfil = perfil.tiene();

        List<CandidatoConRazon> combinados = cronometros.etapa(FICHA_S, nombre,
                MetricasPipeline.ORDENAR,
                () -> adaptativo.ordenar(mezcla, ContextoRanking.ficha(conPerfil),
                        sujeto, perfil.eventos()));
        List<Long> elegidos = cronometros.etapa(FICHA_S, nombre,
                MetricasPipeline.DIVERSIFICAR,
                () -> diversificar(new ArrayList<>(combinados), limite));

        /*
         * Se anota con la razon de CADA item, no con la del modulo. La ficha
         * mezcla parecido por contenido y co-visita, y aplastar las dos en
         * CONTENT_SIMILAR haria imposible saber cual de las dos acierta, que es
         * justo lo que la medicion existe para responder.
         */
        cronometros.etapa(FICHA_S, nombre, MetricasPipeline.ANOTAR,
                () -> registro.anotar(sujeto, modulo, elegidos,
                        razonesDe(combinados), scoresDe(combinados), conPerfil));

        return armar(FICHA_S, modulo, null,
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
        return colaborativo(sujeto, limite, yaUsados, cooldowns.de(sujeto),
                perfiles.estado(sujeto));
    }

    /** La misma, con el enfriamiento y el perfil de la pantalla ya calculados. */
    Carrusel colaborativo(UUID sujeto, int limite, Set<Long> yaUsados, Cooldown enfriamiento,
            EstadoDePerfil perfil) {
        if (sujeto == null) {
            return null;
        }
        ModuloDescubrimiento modulo = ModuloDescubrimiento.OTROS_DESCUBRIERON;
        String nombre = modulo.name();
        List<Long> excluidos = cronometros.etapa(HOME_S, nombre,
                MetricasPipeline.EXCLUSIONES,
                () -> excluidos(sujeto, yaUsados, modulo, enfriamiento));
        int aPedir = limite * FACTOR_SOBREMUESTREO;
        Instant desde = Instant.now().minus(pesos.ventanaColaborativa());

        List<Candidato> porItem = cronometros.etapa(HOME_S, nombre,
                MetricasPipeline.CANDIDATOS_SQL,
                () -> candidatos.porCoVisita(sujeto, SEMILLAS_CO_VISITA, excluidos, aPedir));
        List<Candidato> porVecinos = cronometros.etapa(HOME_S, nombre,
                MetricasPipeline.CANDIDATOS_SQL,
                () -> candidatos.porSujetosSimilares(sujeto,
                        pesos.getSimilitudMinima(), pesos.getSimilitudVecinosConsultados(),
                        desde, pesos.getSimilitudMinAportantes(), excluidos, aPedir));
        cronometros.candidatos(HOME_S, nombre, porItem.size() + porVecinos.size());

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

        cronometros.etapa(HOME_S, nombre, MetricasPipeline.ENFRIAR,
                () -> enfriar(mezcla, modulo, enfriamiento));

        List<CandidatoConRazon> combinados = cronometros.etapa(HOME_S, nombre,
                MetricasPipeline.ORDENAR,
                () -> adaptativo.ordenar(mezcla, ContextoRanking.home(true), sujeto,
                        perfil.eventos()));
        List<Long> elegidos = cronometros.etapa(HOME_S, nombre,
                MetricasPipeline.DIVERSIFICAR,
                () -> diversificar(new ArrayList<>(combinados), limite));

        /*
         * Este modulo es el unico que mezcla dos razones en un carrusel, asi que
         * es el unico donde anotar «la razon del modulo» seria mentira. Se pasa
         * la de cada item, que es justo el dato que permitira saber cual de los
         * dos generadores acierta.
         */
        cronometros.etapa(HOME_S, nombre, MetricasPipeline.ANOTAR,
                () -> registro.anotar(sujeto, modulo, elegidos,
                        razonesDe(combinados), scoresDe(combinados), true));

        return armar(HOME_S, modulo, null,
                "Descubierto por gente con intereses parecidos a los tuyos", elegidos);
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
        return tendenciasDeZona(sujeto, ubigeo, limite, yaUsados, cooldowns.de(sujeto),
                perfiles.estado(sujeto));
    }

    /**
     * La misma, con el enfriamiento ya calculado.
     *
     * <p>Aquí el enfriamiento entra SOLO como exclusión y no como descuento, y
     * no es un olvido. Este carrusel no ordena: lee un orden que calculó el
     * proceso de tendencias y no tiene scores comparables que multiplicar —por
     * eso tampoco anota score—. Aplicar un factor sobre números que no existen
     * habría sido inventarlos.
     */
    Carrusel tendenciasDeZona(UUID sujeto, String ubigeo, int limite, Set<Long> yaUsados,
            Cooldown enfriamiento, EstadoDePerfil perfil) {
        String nombre = ModuloDescubrimiento.LO_MAS_VISTO_EN_TU_ZONA.name();
        TendenciaService.Resultado resultado = cronometros.etapa(HOME_S, nombre,
                MetricasPipeline.CANDIDATOS_SQL, () -> tendencias.enZona(ubigeo, limite * 2));

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
        cronometros.candidatos(HOME_S, nombre, ids.size());
        ids.removeAll(cronometros.etapa(HOME_S, nombre, MetricasPipeline.EXCLUSIONES,
                () -> excluidos(sujeto, yaUsados,
                        ModuloDescubrimiento.LO_MAS_VISTO_EN_TU_ZONA, enfriamiento)));

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
        cronometros.etapa(HOME_S, nombre, MetricasPipeline.ANOTAR,
                () -> anotar(sujeto, ModuloDescubrimiento.LO_MAS_VISTO_EN_TU_ZONA, elegidos,
                        List.of(), perfil.tiene()));

        return new Carrusel(
                ModuloDescubrimiento.LO_MAS_VISTO_EN_TU_ZONA.name(),
                ModuloDescubrimiento.LO_MAS_VISTO_EN_TU_ZONA.titulo(detalle),
                ModuloDescubrimiento.LO_MAS_VISTO_EN_TU_ZONA.origen(),
                "Se está viendo mucho cerca de ti",
                cronometros.etapa(HOME_S, nombre, MetricasPipeline.POR_IDS,
                        () -> productos.porIds(elegidos)));
    }

    /** NUEVAS OPORTUNIDADES · categorías hermanas que todavía no ha pisado. */
    public Carrusel explorar(UUID sujeto, int limite, Set<Long> yaUsados) {
        return explorar(sujeto, limite, yaUsados, cooldowns.de(sujeto));
    }

    /**
     * La misma, con el enfriamiento ya calculado.
     *
     * <p>Este módulo no pasa por el ranker —sus candidatos vienen ya ordenados
     * por el SQL— así que el descuento se aplica aquí y la lista se reordena
     * con él. Sin reordenar, multiplicar el score no habría cambiado nada: el
     * orden de salida seguiría siendo el de entrada, y el enfriamiento sería un
     * número que se calcula y no se usa.
     */
    Carrusel explorar(UUID sujeto, int limite, Set<Long> yaUsados, Cooldown enfriamiento) {
        ModuloDescubrimiento modulo = ModuloDescubrimiento.DESCUBRE_ALGO_NUEVO;
        String nombre = modulo.name();

        List<Long> excluidos = cronometros.etapa(HOME_S, nombre,
                MetricasPipeline.EXCLUSIONES,
                () -> excluidos(sujeto, yaUsados, modulo, enfriamiento));
        List<Candidato> crudos = cronometros.etapa(HOME_S, nombre,
                MetricasPipeline.CANDIDATOS_SQL,
                () -> candidatos.paraExplorar(sujeto, excluidos,
                        limite * FACTOR_SOBREMUESTREO));
        cronometros.candidatos(HOME_S, nombre, crudos.size());

        List<CandidatoConRazon> mezcla = new ArrayList<>();
        for (Candidato c : crudos) {
            mezcla.add(CandidatoConRazon.de(c, Origen.EXPLORACION,
                    RazonRecomendacion.EXPLORATION));
        }
        cronometros.etapa(HOME_S, nombre, MetricasPipeline.ENFRIAR, () -> {
            enfriar(mezcla, modulo, enfriamiento);
            mezcla.sort(Comparator.comparingDouble(CandidatoConRazon::score).reversed());
        });

        List<Long> elegidos = cronometros.etapa(HOME_S, nombre,
                MetricasPipeline.DIVERSIFICAR,
                () -> diversificar(new ArrayList<>(mezcla), limite));
        cronometros.etapa(HOME_S, nombre, MetricasPipeline.ANOTAR,
                () -> anotar(sujeto, modulo, elegidos, new ArrayList<>(mezcla), true));

        return armar(HOME_S, modulo, null, "Algo distinto de lo que sueles mirar", elegidos);
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
        return populares(sujeto, limite, yaUsados, cooldowns.de(sujeto),
                perfiles.estado(sujeto));
    }

    /** La misma, con el enfriamiento y el perfil de la pantalla ya calculados. */
    Carrusel populares(UUID sujeto, int limite, Set<Long> yaUsados, Cooldown enfriamiento,
            EstadoDePerfil perfil) {
        ModuloDescubrimiento modulo = ModuloDescubrimiento.POPULARES;
        String nombre = modulo.name();
        List<Long> excluidos = cronometros.etapa(HOME_S, nombre,
                MetricasPipeline.EXCLUSIONES, () -> {
                    if (sujeto == null) {
                        List<Long> sinSujeto = new ArrayList<>(yaUsados);
                        sinSujeto.add(NINGUNO);
                        return sinSujeto;
                    }
                    return excluidos(sujeto, yaUsados, modulo, enfriamiento);
                });
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
        List<Candidato> nuevos = cronometros.etapa(HOME_S, nombre,
                MetricasPipeline.CANDIDATOS_SQL,
                () -> candidatos.catalogoNuevo(pesos.fronteraCatalogoNuevo(), excluidos,
                        pesos.getCatalogoNuevoCupo()));
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

        List<Candidato> crudos = cronometros.etapa(HOME_S, nombre,
                MetricasPipeline.CANDIDATOS_SQL,
                () -> candidatos.populares(null, sinRepetir, limite * FACTOR_SOBREMUESTREO));
        cronometros.candidatos(HOME_S, nombre, crudos.size() + nuevos.size());

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
        cronometros.etapa(HOME_S, nombre, MetricasPipeline.ENFRIAR,
                () -> enfriar(mezcla, modulo, enfriamiento));

        boolean conPerfilAqui = perfil.tiene();
        List<CandidatoConRazon> combinados = cronometros.etapa(HOME_S, nombre,
                MetricasPipeline.ORDENAR,
                () -> adaptativo.ordenar(mezcla, ContextoRanking.home(conPerfilAqui),
                        sujeto, perfil.eventos()));
        List<Long> elegidos = cronometros.etapa(HOME_S, nombre,
                MetricasPipeline.DIVERSIFICAR,
                () -> reservarCupoDeNovedad(
                        diversificar(new ArrayList<>(combinados), limite), combinados));
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
        cronometros.etapa(HOME_S, nombre, MetricasPipeline.ANOTAR,
                () -> registro.anotar(sujeto, modulo, elegidos,
                        razonesDe(combinados), scoresDe(combinados), conPerfilAqui));

        return armar(HOME_S, modulo, null,
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
     * Lo que NO puede aparecer en ESTE carrusel.
     *
     * <p>Tres listas que se suman y ninguna que reste. Lo descartado a mano, lo
     * que ya se ha enseñado aquí hasta pasarse del corte, y lo que otro
     * carrusel de esta misma pantalla ya se llevó.
     *
     * <h4>Por qué ahora recibe el módulo</h4>
     *
     * <p>Porque el enfriamiento dejó de ser global. Lo descartado sigue
     * saliendo de todas partes —«no me interesa» es una respuesta sobre el
     * producto, no sobre el sitio donde apareció—, pero haber visto algo tres
     * veces en «relacionados» no es motivo para borrarlo de «lo más popular»,
     * donde quizá no ha salido nunca.
     *
     * <h4>Esto se aplica ANTES de generar candidatos</h4>
     *
     * <p>La lista viaja al {@code NOT IN} de cada consulta, así que lo excluido
     * no llega a existir como candidato. No es una preferencia de estilo: es lo
     * que hace imposible que el ranker —o la exploración, que elige entre los
     * mismos— reintroduzca algo que ya estaba fuera. Un filtro aplicado después
     * de ordenar sería una promesa que depende de que nadie cambie el orden.
     */
    private List<Long> excluidos(UUID sujeto, Set<Long> yaUsados,
            ModuloDescubrimiento modulo, Cooldown enfriamiento) {
        Set<Long> fuera = new HashSet<>(yaUsados);
        fuera.addAll(descartes.idsDescartados(sujeto, TipoItem.PRODUCTO));
        fuera.addAll(enfriamiento.bloqueadosEn(modulo));
        // `NOT IN ()` es un error de sintaxis, no una lista vacía.
        fuera.add(NINGUNO);
        return new ArrayList<>(fuera);
    }

    /**
     * Descuenta a cada candidato lo que ya se le ha insistido en este carrusel.
     *
     * <p>Va sobre la mezcla ya construida y ANTES del ranker, que es el orden
     * que pide la etapa: el ranker recibe scores que ya llevan el enfriamiento
     * dentro y no tiene que saber nada de él. Lo que no tiene castigo se deja
     * intacto y no se reconstruye, porque el caso normal es justamente ese.
     *
     * <p>Lo que ha pasado del corte no está aquí: ese salió en las exclusiones
     * y nunca llegó a ser candidato. Este método solo gradúa; no excluye a
     * nadie ni devuelve a nadie.
     */
    private void enfriar(List<CandidatoConRazon> mezcla, ModuloDescubrimiento modulo,
            Cooldown enfriamiento) {
        mezcla.replaceAll(c -> {
            double factor = enfriamiento.factor(modulo, c.itemId());
            return factor == 1.0 ? c : c.conScore(c.score() * factor);
        });
    }

    /** Lo último que miró, para «sigue donde lo dejaste». */
    public List<Long> vistosRecientemente(UUID sujeto, int limite) {
        return eventos.itemsVistosRecientemente(sujeto, TipoItem.PRODUCTO,
                PageRequest.of(0, limite));
    }

    /**
     * El armado final, que es la etapa que más fácil se olvida al medir.
     *
     * <p>{@code porIds} carga los productos con sus imágenes para pintarlos, y
     * es de las pocas partes del pipeline cuyo coste crece con lo que se
     * devuelve y no con lo que se descarta. Va cronometrada por módulo porque
     * cada carrusel pide su propio lote.
     */
    private Carrusel armar(String superficie, ModuloDescubrimiento modulo, String detalle,
            String motivo, List<Long> ids) {
        if (ids.isEmpty()) {
            return null;
        }
        return new Carrusel(modulo.name(), modulo.titulo(detalle), modulo.origen(), motivo,
                cronometros.etapa(superficie, modulo.name(), MetricasPipeline.POR_IDS,
                        () -> productos.porIds(ids)));
    }

    private void agregar(List<Carrusel> salida, Carrusel carrusel, Set<Long> yaUsados) {
        if (carrusel == null || carrusel.items().isEmpty()) {
            return;
        }
        salida.add(carrusel);
        carrusel.items().forEach(p -> yaUsados.add(p.id()));
    }
}
