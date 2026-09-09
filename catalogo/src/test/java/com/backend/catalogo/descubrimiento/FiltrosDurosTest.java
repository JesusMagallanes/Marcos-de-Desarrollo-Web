package com.backend.catalogo.descubrimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

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

    @Captor
    private ArgumentCaptor<List<Long>> excluidos;

    private RecomendacionService servicio() {
        PesosDescubrimiento pesos = new PesosDescubrimiento();
        return new RecomendacionService(candidatos, descartes, impresiones, eventos,
                perfiles, tendencias, productos,
                new RankerHibrido(impresiones, pesos),
                new MetricasDescubrimiento(new SimpleMeterRegistry()), pesos);
    }

    @Test
    @DisplayName("«lo más popular» no repesca lo que el sujeto descartó")
    void popularesRespetaElDescarte() {
        when(descartes.idsDescartados(SUJETO, TipoItem.PRODUCTO)).thenReturn(List.of(7L));
        when(impresiones.itemsConFatiga(eq(SUJETO), anyString(), any(Instant.class), anyInt()))
                .thenReturn(List.of(9L));
        when(candidatos.populares(isNull(), anyList(), anyInt())).thenReturn(List.of());

        servicio().populares(SUJETO, 12, new LinkedHashSet<>(List.of(3L)));

        verify(candidatos).populares(isNull(), excluidos.capture(), anyInt());
        assertThat(excluidos.getValue())
                .as("lo descartado, lo que ya cansa y lo que otro carrusel se llevó")
                .contains(7L, 9L, 3L);
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
