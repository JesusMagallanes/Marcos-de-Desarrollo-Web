package com.backend.catalogo.descubrimiento;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.backend.catalogo.producto.ProductoService;

/**
 * La etapa que impide que un carrusel sean cuatro clones.
 *
 * <p>Es el fallo característico de un recomendador que solo ordena por score:
 * si a alguien le interesan los monitores, los diez mejores candidatos son
 * diez monitores, todos puntúan alto por la misma razón, y la lista es peor
 * que una con la mitad de precisión y cinco categorías distintas. En
 * descubrimiento la diversidad no es un adorno del ranking: es parte del
 * objetivo.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Diversidad del carrusel")
class DiversidadTest {

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

    private RecomendacionService servicio() {
        PesosDescubrimiento pesos = new PesosDescubrimiento();
        return new RecomendacionService(candidatos, descartes, impresiones, eventos,
                perfiles, tendencias, productos,
                new RankerHibrido(impresiones, pesos),
                new MetricasDescubrimiento(new SimpleMeterRegistry()), registro, pesos);
    }

    /** Un candidato con su categoría y su marca. */
    private Candidato candidato(long id, long categoria, Long marca, double score) {
        return new Candidato() {
            public Long getItemId() {
                return id;
            }

            public Long getCategoriaId() {
                return categoria;
            }

            public Long getMarcaId() {
                return marca;
            }

            public Double getScore() {
                return score;
            }
        };
    }

    @Test
    @DisplayName("no se cuelan más de N de la misma categoría")
    void cuotaPorCategoria() {
        // Ocho monitores seguidos, que es exactamente lo que devolvería un
        // ranking puro para alguien interesado en monitores.
        List<Candidato> todosMonitores = List.of(
                candidato(1, 100L, 10L, 9.0), candidato(2, 100L, 11L, 8.5),
                candidato(3, 100L, 12L, 8.0), candidato(4, 100L, 13L, 7.5),
                candidato(5, 100L, 14L, 7.0), candidato(6, 100L, 15L, 6.5),
                candidato(7, 200L, 16L, 6.0), candidato(8, 300L, 17L, 5.5));

        List<Long> elegidos = servicio().diversificar(todosMonitores, 5);

        // Los tres primeros son monitores (el tope), y después entran otras
        // categorías aunque puntúen menos.
        assertThat(elegidos).hasSize(5);
        assertThat(elegidos.subList(0, 3)).containsExactly(1L, 2L, 3L);
        assertThat(elegidos).contains(7L, 8L);
    }

    @Test
    @DisplayName("tampoco más de N de la misma marca")
    void cuotaPorMarca() {
        List<Candidato> mismaMarca = List.of(
                candidato(1, 100L, 10L, 9.0), candidato(2, 200L, 10L, 8.5),
                candidato(3, 300L, 10L, 8.0), candidato(4, 400L, 11L, 7.5));

        List<Long> elegidos = servicio().diversificar(mismaMarca, 4);

        // El tercero de la marca 10 cede su sitio al de la marca 11.
        assertThat(elegidos.subList(0, 3)).containsExactly(1L, 2L, 4L);
    }

    @Test
    @DisplayName("antes que dejar huecos, se rellena con lo apartado")
    void rellenaEnVezDeDejarHuecos() {
        /*
         * Un carrusel con tres tarjetas y un espacio vacío se ve roto. Es
         * preferible algo de repetición a una fila incompleta, así que lo que
         * las cuotas apartaron vuelve al final si sobran huecos.
         */
        List<Candidato> soloUnaCategoria = List.of(
                candidato(1, 100L, 10L, 9.0), candidato(2, 100L, 11L, 8.0),
                candidato(3, 100L, 12L, 7.0), candidato(4, 100L, 13L, 6.0),
                candidato(5, 100L, 14L, 5.0));

        List<Long> elegidos = servicio().diversificar(soloUnaCategoria, 5);

        assertThat(elegidos).hasSize(5);
        // El orden por score se respeta dentro de lo que entró por cuota y de
        // lo que se recuperó después.
        assertThat(elegidos).startsWith(1L, 2L, 3L);
    }

    @Test
    @DisplayName("una marca nula no bloquea el carrusel")
    void marcaNula() {
        List<Candidato> sinMarca = List.of(
                candidato(1, 100L, null, 9.0), candidato(2, 200L, null, 8.0),
                candidato(3, 300L, null, 7.0));

        assertThat(servicio().diversificar(sinMarca, 3)).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("sin candidatos devuelve vacío, no revienta")
    void sinCandidatos() {
        assertThat(servicio().diversificar(List.of(), 5)).isEmpty();
    }
}
