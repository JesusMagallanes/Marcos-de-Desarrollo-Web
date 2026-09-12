package com.backend.catalogo.descubrimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.backend.catalogo.descubrimiento.config.PesosDescubrimiento;

/**
 * La etapa que hace comparables señales que no lo son.
 *
 * <p>El fallo que evita es silencioso y por eso peligroso: cada generador
 * puntúa en unidades suyas —el perfil suma scores que llegan a 40, el
 * colaborativo suma cosenos que no pasan de 3— y si se mezclan tal cual, gana
 * siempre el de números más grandes. El sistema parecería estar combinando
 * señales cuando en realidad estaría ignorando una.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Ranker híbrido")
class RankerHibridoTest {

    @Mock
    private ImpresionRepository impresiones;

    private PesosDescubrimiento pesos;
    private RankerHibrido ranker;

    @BeforeEach
    void preparar() {
        pesos = new PesosDescubrimiento();
        ranker = new RankerHibrido(impresiones, pesos);
        // Por defecto nadie está sobreexpuesto; cada prueba lo cambia si quiere.
        when(impresiones.contarPorItem(anyList(), any(Instant.class), anyInt())).thenReturn(List.<Object[]>of());
    }

    private CandidatoConRazon candidato(long id, double score, Origen origen,
            RazonRecomendacion razon) {
        return new CandidatoConRazon(id, 100L, 10L, score, origen, razon);
    }

    @Test
    @DisplayName("las unidades de cada generador no deciden el orden")
    void seNormalizaAntesDePonderar() {
        /*
         * El colaborativo puntúa bajísimo comparado con el personal, pero es el
         * mejor de los suyos. Sin normalizar quedaría sepultado por el peor
         * candidato personal, que vale 30 veces más solo por la escala.
         */
        List<CandidatoConRazon> mezcla = List.of(
                candidato(1, 40.0, Origen.PERSONAL, RazonRecomendacion.PERSONAL_INTEREST),
                candidato(2, 4.0, Origen.PERSONAL, RazonRecomendacion.PERSONAL_INTEREST),
                candidato(3, 1.2, Origen.COHORTE, RazonRecomendacion.CO_VIEWED));

        List<CandidatoConRazon> ordenados = ranker.combinar(mezcla);

        assertThat(ordenados).extracting(CandidatoConRazon::itemId)
                .as("el mejor personal manda; el mejor colaborativo supera al personal flojo")
                .containsExactly(1L, 3L, 2L);
    }

    @Test
    @DisplayName("los pesos por origen se respetan a igualdad de mérito")
    void elPesoDelOrigenDesempata() {
        // Los dos son lo mejor de su grupo: normalizados valen 1,0 los dos, así
        // que lo único que queda es cuánto se cree a cada fuente.
        List<CandidatoConRazon> mezcla = List.of(
                candidato(1, 5.0, Origen.PERSONAL, RazonRecomendacion.PERSONAL_INTEREST),
                candidato(2, 900.0, Origen.TENDENCIA, RazonRecomendacion.LOCAL_TREND));

        assertThat(ranker.combinar(mezcla)).extracting(CandidatoConRazon::itemId)
                .containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("lo que ya se enseña demasiado baja")
    void laSobreexposicionSeCastiga() {
        when(impresiones.contarPorItem(anyList(), any(Instant.class), anyInt()))
                .thenReturn(List.<Object[]>of(new Object[] { 1L, 5000L }));

        List<CandidatoConRazon> mezcla = List.of(
                candidato(1, 10.0, Origen.PERSONAL, RazonRecomendacion.PERSONAL_INTEREST),
                candidato(2, 9.0, Origen.PERSONAL, RazonRecomendacion.PERSONAL_INTEREST));

        assertThat(ranker.combinar(mezcla)).extracting(CandidatoConRazon::itemId)
                .as("el que iba primero cae por llevar cinco mil impresiones encima")
                .containsExactly(2L, 1L);
    }

    @Test
    @DisplayName("un producto que llega por dos caminos no se cuenta dos veces")
    void elMismoItemNoSeSuma() {
        /*
         * Sumar los dos scores premiaría estar en todas partes, que es
         * exactamente el sesgo que este ranker existe para frenar. Se queda con
         * el mejor y con la razón que lo consiguió.
         */
        List<CandidatoConRazon> mezcla = List.of(
                candidato(1, 10.0, Origen.PERSONAL, RazonRecomendacion.PERSONAL_INTEREST),
                candidato(1, 10.0, Origen.COHORTE, RazonRecomendacion.CO_VIEWED),
                candidato(2, 10.0, Origen.PERSONAL, RazonRecomendacion.PERSONAL_INTEREST));

        List<CandidatoConRazon> ordenados = ranker.combinar(mezcla);

        assertThat(ordenados).hasSize(2);
        assertThat(ordenados.get(0).razon())
                .as("gana el camino de más peso, y se recuerda cuál fue")
                .isEqualTo(RazonRecomendacion.PERSONAL_INTEREST);
    }

    @Test
    @DisplayName("sin candidatos no se consulta nada ni se rompe nada")
    void listaVacia() {
        assertThat(ranker.combinar(List.of())).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(impresiones);
    }
}
