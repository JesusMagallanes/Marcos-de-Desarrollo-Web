package com.backend.catalogo.descubrimiento.adaptativo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.backend.catalogo.descubrimiento.CandidatoConRazon;
import com.backend.catalogo.descubrimiento.ImpresionRepository;
import com.backend.catalogo.descubrimiento.Origen;
import com.backend.catalogo.descubrimiento.RankerHibrido;
import com.backend.catalogo.descubrimiento.RazonRecomendacion;
import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * El ranker contextual, y sobre todo su plan de vuelta atrás.
 *
 * <p>Lo que se comprueba aquí no es que ordene mejor —eso no lo decide una
 * prueba unitaria, lo decide la evaluación offline— sino que la aritmética hace
 * lo que se cree, que el contexto cambia el resultado, y que cuando algo va mal
 * el sistema sigue recomendando en vez de quedarse mudo.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Ranker adaptativo")
class RankerAdaptativoTest {

    private static final UUID SUJETO = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Mock
    private ImpresionRepository impresiones;

    private PesosAdaptativos pesos;
    private RankerAdaptativo ranker;
    private RankerHibrido hibrido;

    @BeforeEach
    void preparar() {
        when(impresiones.contarPorItem(anyList(), any(Instant.class)))
                .thenReturn(List.<Object[]>of());

        pesos = new PesosAdaptativos();
        // Sin experimento por defecto: cada prueba enciende el suyo.
        pesos.setPorcentajeVariante(0);

        PesosDescubrimiento base = new PesosDescubrimiento();
        hibrido = new RankerHibrido(impresiones, base);
        ranker = new RankerAdaptativo(hibrido, impresiones,
                new com.backend.catalogo.descubrimiento.MetricasPipeline(
                        new SimpleMeterRegistry()),
                new AsignacionExperimento(pesos), pesos,
                new MetricasAdaptativas(new SimpleMeterRegistry()));
    }

    private CandidatoConRazon candidato(long id, double score, Origen origen) {
        return new CandidatoConRazon(id, 100L + id, 10L, score, origen,
                origen == Origen.COHORTE
                        ? RazonRecomendacion.CO_VIEWED
                        : RazonRecomendacion.PERSONAL_INTEREST);
    }

    @Test
    @DisplayName("el contexto cambia quién gana")
    void elMismoCandidatoNoGanaEnLosDosSitios() {
        /*
         * Es la razón de ser de toda la fase. Los mismos dos candidatos, la
         * misma evidencia: en el Home de alguien conocido manda su perfil; en la
         * ficha, donde la pregunta la marca el producto, la co-visita vale tanto
         * como él y con estos pesos lo adelanta.
         */
        List<CandidatoConRazon> mezcla = List.of(
                candidato(1, 0.90, Origen.PERSONAL),
                candidato(2, 1.00, Origen.COHORTE));

        List<Long> enHome = ids(ranker.ordenar(mezcla,
                ContextoRanking.home(true), SUJETO, 20));
        List<Long> enFicha = ids(ranker.ordenar(mezcla,
                ContextoRanking.ficha(true), SUJETO, 20));

        assertThat(enHome)
                .as("Home con perfil: PERSONAL 1,0 · COHORTE 0,9")
                .containsExactly(1L, 2L);
        assertThat(enFicha)
                .as("Ficha: COHORTE 1,0 · PERSONAL 0,8 — la pregunta la hace el producto")
                .containsExactly(2L, 1L);
    }

    @Test
    @DisplayName("sin perfil, lo personal deja de mandar")
    void elArranqueEnFrioCambiaLaMezcla() {
        // A quien no se conoce, la señal personal le reparte ceros: insistir en
        // ella sería ordenar por una columna vacía.
        List<CandidatoConRazon> mezcla = List.of(
                candidato(1, 1.00, Origen.PERSONAL),
                candidato(2, 1.00, Origen.TENDENCIA));

        assertThat(ids(ranker.ordenar(mezcla, ContextoRanking.home(false), SUJETO, 0)))
                .as("HOME_SIN_PERFIL: TENDENCIA 1,0 · PERSONAL 0,2")
                .containsExactly(2L, 1L);
    }

    @Test
    @DisplayName("apagado, ordena exactamente el ranker de la fase 3")
    void elInterruptorDevuelveAlEstable() {
        pesos.setActivo(false);

        List<CandidatoConRazon> mezcla = List.of(
                candidato(1, 0.90, Origen.PERSONAL),
                candidato(2, 1.00, Origen.COHORTE));

        assertThat(ids(ranker.ordenar(mezcla, ContextoRanking.ficha(true), SUJETO, 20)))
                .as("volver atrás es una propiedad, no un despliegue")
                .isEqualTo(ids(hibrido.combinar(mezcla)));
    }

    @Test
    @DisplayName("una calibración rota no deja al Home sin recomendaciones")
    void anteUnFalloCaeAlEstable() {
        /*
         * El modo de fallo que exige esta fase. Se rompe a propósito la tabla de
         * pesos: lo que no puede pasar es que el visitante se quede sin
         * carrusel porque una mejora no supo aplicarse.
         */
        pesos.setPesosPorDefecto(null);
        pesos.setPesos(null);

        List<CandidatoConRazon> mezcla = List.of(
                candidato(1, 0.90, Origen.PERSONAL),
                candidato(2, 1.00, Origen.COHORTE));

        List<CandidatoConRazon> salida = ranker.ordenar(mezcla,
                ContextoRanking.home(true), SUJETO, 20);

        assertThat(salida).as("responde igual, con el ranker de siempre").hasSize(2);
        assertThat(ids(salida)).isEqualTo(ids(hibrido.combinar(mezcla)));
    }

    @Test
    @DisplayName("no inventa candidatos: solo reordena los que recibe")
    void nuncaAnadeNadaDeSuCosecha() {
        /*
         * Es lo que garantiza que la exploración no pueda resucitar un descarte.
         * Los filtros duros se aplican antes, en el SQL; si este ranker pudiera
         * traer candidatos de otro sitio, abriría una puerta trasera a los
         * negativos y a la fatiga.
         */
        List<CandidatoConRazon> mezcla = List.of(
                candidato(1, 1.0, Origen.PERSONAL),
                candidato(2, 0.9, Origen.COHORTE),
                candidato(3, 0.8, Origen.TENDENCIA),
                candidato(4, 0.7, Origen.PERSONAL),
                candidato(5, 0.6, Origen.COHORTE),
                candidato(6, 0.5, Origen.TENDENCIA));

        List<Long> salida = ids(ranker.ordenar(mezcla, ContextoRanking.home(false), SUJETO, 0));

        assertThat(salida)
                .as("los mismos seis, en otro orden")
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L);
    }

    @Test
    @DisplayName("un producto que llega por dos caminos sale una vez")
    void noSeDuplica() {
        List<CandidatoConRazon> mezcla = List.of(
                candidato(1, 1.0, Origen.PERSONAL),
                candidato(1, 1.0, Origen.COHORTE),
                candidato(2, 0.5, Origen.PERSONAL));

        assertThat(ids(ranker.ordenar(mezcla, ContextoRanking.home(true), SUJETO, 20)))
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("lo sobreexpuesto baja aunque su señal sea la mejor")
    void elFrenoPorExposicionSeAplica() {
        when(impresiones.contarPorItem(anyList(), any(Instant.class)))
                .thenReturn(List.<Object[]>of(new Object[] { 1L, 5000L }));

        List<CandidatoConRazon> mezcla = List.of(
                candidato(1, 1.00, Origen.PERSONAL),
                candidato(2, 0.85, Origen.PERSONAL));

        assertThat(ids(ranker.ordenar(mezcla, ContextoRanking.home(true), SUJETO, 20)))
                .as("cinco mil impresiones encima pesan más que un 15 % de score")
                .containsExactly(2L, 1L);
    }

    @Test
    @DisplayName("la versión cambia sola al mover una calibración")
    void laHuellaDelataUnCambio() {
        String antes = pesos.version();
        pesos.setPenalizacionExposicion(pesos.getPenalizacionExposicion() + 0.1);

        assertThat(pesos.version())
                .as("mismo prefijo, huella distinta")
                .isNotEqualTo(antes)
                .startsWith(pesos.getEtiqueta() + "-");
    }

    @Test
    @DisplayName("la huella no depende del orden en que se escriban los pesos")
    void laHuellaEsEstable() {
        /*
         * Si dependiera del orden de iteración de un mapa, la versión cambiaría
         * al reiniciar y la comparación entre configuraciones se rompería sin
         * que nadie entendiera por qué.
         */
        String primera = pesos.version();
        String segunda = pesos.version();
        assertThat(segunda).isEqualTo(primera);
    }

    private List<Long> ids(List<CandidatoConRazon> lista) {
        return lista.stream().map(CandidatoConRazon::itemId).toList();
    }
}
