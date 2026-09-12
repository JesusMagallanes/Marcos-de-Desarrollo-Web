package com.backend.catalogo.descubrimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.backend.catalogo.producto.ProductoService;

/**
 * Que lo descartado no vuelva, en NINGÚN carrusel.
 *
 * <p>«Lo más popular» nació como arranque en frío —quien no tiene rastro no
 * tiene nada personal que ofrecerle— y por eso no recibía el sujeto. Pero se
 * añade al Home siempre, también al de quien sí tiene rastro, porque es el
 * relleno que evita una pantalla a medias. El resultado era que se saltaba los
 * filtros duros: alguien marcaba «no me interesa» y el producto reaparecía en
 * la carga siguiente, arriba del todo. Descartar algo y verlo volver es peor
 * que no poder descartarlo: dice que el botón no sirve.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Filtros duros")
class FiltrosDurosTest {

    private static final UUID SUJETO = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Mock
    private CandidatoRepository candidatos;
    @Mock
    private ItemDescartadoRepository descartes;
    @Mock
    private ImpresionRepository impresiones;
    @Mock
    private EventoInteraccionRepository eventos;
    @Mock
    private PerfilService perfiles;
    @Mock
    private TendenciaService tendencias;
    @Mock
    private ProductoService productos;

    /*
     * Doble y no instancia real: anotar lo servido escribe en la base y estas
     * son pruebas unitarias. Que el registro NO estorbe al ranking es parte de
     * lo que se comprueba aqui sin decirlo.
     */
    @Mock
    private RegistroRecomendacionService registro;

    /*
     * Doble tambien: estas pruebas comprueban la diversidad y los filtros duros,
     * que son etapas ANTERIORES y POSTERIORES al ranking. Meter el ranker
     * adaptativo de verdad las haria depender de su calibracion, y entonces
     * cambiar un peso rompería pruebas que no hablan de pesos.
     */
    @Mock
    private com.backend.catalogo.descubrimiento.adaptativo.RankerAdaptativo adaptativo;

    /*
     * Doble tambien: la elegibilidad solo interviene en la ruta de tendencias,
     * que estas pruebas no ejercitan. Usar la real las ataria a una consulta
     * contra la base que aqui no existe.
     */
    @Mock
    private ElegibilidadService elegibilidad;

    /*
     * Doble: la intencion de sesion es una consulta agregada contra la base que
     * estas pruebas no levantan. Lo que comprueban —diversidad y filtros duros—
     * es anterior y posterior a esa senal.
     */
    @Mock
    private SesionService sesiones;

    @Captor
    private ArgumentCaptor<List<Long>> excluidos;

    /*
     * El perfil se resuelve UNA vez por peticion desde el bloque G, asi que el
     * doble tiene que devolver algo. `sinNada()` es el caso por defecto de estas
     * pruebas: hablan de filtros duros y de diversidad, no de personalizacion.
     */
    @BeforeEach
    void sinPerfil() {
        lenient().when(perfiles.estado(any()))
                .thenReturn(PerfilService.EstadoDePerfil.sinNada());
    }

    private RecomendacionService servicio() {
        PesosDescubrimiento pesos = new PesosDescubrimiento();
        /*
         * El enfriamiento es REAL y no un doble, a proposito. Lo unico que lee
         * es `impresiones`, que ya esta mockeado, y usarlo de verdad es lo que
         * permite comprobar que sin sujeto no toca la base — que es justo lo
         * que afirma una de estas pruebas.
         */
        CooldownService enfriamiento =
                new CooldownService(impresiones, pesos, new SimpleMeterRegistry());
        return new RecomendacionService(candidatos, descartes, eventos,
                perfiles, tendencias, productos,
                new RankerHibrido(impresiones, pesos), adaptativo,
                new MetricasDescubrimiento(new SimpleMeterRegistry()), registro, elegibilidad,
                sesiones, enfriamiento,
                /*
                 * Cronometros reales sobre un registro de juguete. Medir no
                 * puede cambiar el resultado, y usarlos de verdad aqui es lo
                 * que hace que estas pruebas lo comprueben sin decirlo.
                 */
                new MetricasPipeline(new SimpleMeterRegistry()), pesos);
    }

    @Test
    @DisplayName("«lo más popular» no repesca lo que el sujeto descartó")
    void popularesRespetaElDescarte() {
        when(descartes.idsDescartados(SUJETO, TipoItem.PRODUCTO)).thenReturn(List.of(7L));
        /*
         * Seis impresiones del 9 en POPULARES y sin un solo clic: pasado el
         * corte, asi que ese carrusel no puede ofrecerlo. Las columnas son las
         * de `exposicionPorModulo`: item, modulo, veces, clics del item.
         */
        when(impresiones.exposicionPorModulo(eq(SUJETO), anyString(), any(Instant.class),
                any(Instant.class)))
                .thenReturn(List.<Object[]>of(new Object[] {9L, "POPULARES", 6, 0L}));
        when(candidatos.populares(isNull(), anyList(), anyInt())).thenReturn(List.of());

        servicio().populares(SUJETO, 12, new LinkedHashSet<>(List.of(3L)));

        verify(candidatos).populares(isNull(), excluidos.capture(), anyInt());
        assertThat(excluidos.getValue())
                .as("lo descartado, lo que ya cansa y lo que otro carrusel se llevó")
                .contains(7L, 9L, 3L);
    }

    @Test
    @DisplayName("el enfriamiento de un carrusel no borra el producto de otro")
    void elEnfriamientoNoCruzaDeModulo() {
        /*
         * Seis impresiones en «relacionados» bastan para que el producto salga
         * de ESE carrusel. En «lo mas popular», donde no ha aparecido nunca,
         * cuentan a peso reducido —2,1 efectivas— y no llegan al corte: el
         * producto sigue compitiendo, penalizado pero presente.
         *
         * Es la diferencia con la regla anterior, que era ciega al modulo y lo
         * borraba de toda la pantalla por lo que habia pasado en un sitio.
         */
        when(impresiones.exposicionPorModulo(eq(SUJETO), anyString(), any(Instant.class),
                any(Instant.class)))
                .thenReturn(List.<Object[]>of(new Object[] {9L, "RELACIONADOS", 6, 0L}));
        when(candidatos.populares(isNull(), anyList(), anyInt())).thenReturn(List.of());

        servicio().populares(SUJETO, 12, new LinkedHashSet<>());

        verify(candidatos).populares(isNull(), excluidos.capture(), anyInt());
        assertThat(excluidos.getValue())
                .as("lo que cansa en un carrusel no se borra del resto de la pantalla")
                .doesNotContain(9L);
    }

    @Test
    @DisplayName("«no me interesa» sí sale de todos los carruseles")
    void elDescarteSiEsGlobal() {
        /*
         * La otra mitad de la frase anterior. El enfriamiento es local porque
         * habla de insistencia; el descarte es global porque habla del
         * producto. Sin esta distincion, graduar la fatiga habria sido la excusa
         * para que «no me interesa» dejara de significar lo que dice.
         */
        when(descartes.idsDescartados(SUJETO, TipoItem.PRODUCTO)).thenReturn(List.of(7L));
        when(candidatos.populares(isNull(), anyList(), anyInt())).thenReturn(List.of());

        servicio().populares(SUJETO, 12, new LinkedHashSet<>());

        verify(candidatos).populares(isNull(), excluidos.capture(), anyInt());
        assertThat(excluidos.getValue()).contains(7L);
    }

    @Test
    @DisplayName("sin sujeto no se consulta nada suyo: es el arranque en frío de verdad")
    void sinSujetoNoConsultaPerfil() {
        when(candidatos.populares(isNull(), anyList(), anyInt())).thenReturn(List.of());

        servicio().populares(null, 12, new LinkedHashSet<>());

        verifyNoInteractions(descartes);
        verifyNoInteractions(impresiones);
    }
}
