package com.backend.catalogo.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.backend.catalogo.ia.dto.IaDtos.ComparacionRequest;
import com.backend.catalogo.producto.EstadoModeracion;
import com.backend.catalogo.producto.ProductoService;
import com.backend.catalogo.producto.dto.ProductoDtos.ProductoResponse;
import com.backend.catalogo.shared.ia.ClienteIa;
import com.backend.catalogo.shared.ia.GuardiaPrompt;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.json.JsonMapper;

/**
 * La caché de respuestas del modelo.
 *
 * <p>Lo que hay que demostrar no es que guarde —eso es fácil— sino que no sirva
 * datos viejos: quien pregunta dos veces por el mismo producto tiene que ver el
 * precio de hoy, aunque la frase del modelo venga de hace diez minutos.
 */
@DisplayName("Caché de respuestas de IA")
class CacheIaTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final SimpleMeterRegistry metricas = new SimpleMeterRegistry();
    private ClienteIa ia;
    private ProductoService productos;
    private Comparador comparador;

    @BeforeEach
    void preparar() {
        ia = mock(ClienteIa.class);
        when(ia.disponible()).thenReturn(true);
        when(ia.json(eq(Comparador.FUNCION), any(), any(), anyInt())).thenReturn(
                Optional.of(json.readTree("{\"explicacion\":\"La segunda es mas barata\",\"recomendadoId\":2}")));
        productos = mock(ProductoService.class);
        comparador = new Comparador(ia, new GuardiaPrompt(), productos, new CacheIa(metricas));
    }

    private static ProductoResponse producto(long id, String precio) {
        return new ProductoResponse(id, "P" + id, "desc", null, new BigDecimal(precio), null, null, null, null,
                null, new BigDecimal(precio), false, 4.5, 2L, null, List.of(), 3, 1L, "Laptops", 2L, "ASUS",
                99L, EstadoModeracion.APROBADO, null, List.of());
    }

    private void catalogo(String precioDelSegundo) {
        when(productos.obtener(1L)).thenReturn(producto(1, "4000"));
        when(productos.obtener(2L)).thenReturn(producto(2, precioDelSegundo));
    }

    @Test
    @DisplayName("la misma comparación dos veces se pregunta una sola vez")
    void seRepiteSinPreguntar() {
        catalogo("3000");
        var peticion = new ComparacionRequest(List.of(1L, 2L), "para estudiar");

        var primera = comparador.comparar(peticion);
        var segunda = comparador.comparar(peticion);

        assertThat(primera.explicacion()).isEqualTo("La segunda es mas barata");
        assertThat(segunda.explicacion()).isEqualTo(primera.explicacion());
        verify(ia, times(1)).json(eq(Comparador.FUNCION), any(), any(), anyInt());
        assertThat(metricas.find("smartzone_ia_cache_total").tag("resultado", "acierto").counter().count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("si cambia el precio, se vuelve a preguntar: la tabla guardada forma parte de la clave")
    void elPrecioInvalidaLoGuardado() {
        catalogo("3000");
        var peticion = new ComparacionRequest(List.of(1L, 2L), "para estudiar");
        comparador.comparar(peticion);

        catalogo("2500");
        var despues = comparador.comparar(peticion);

        assertThat(despues.filas().get(0).valores()).containsExactly("S/ 4000", "S/ 2500");
        verify(ia, times(2)).json(eq(Comparador.FUNCION), any(), any(), anyInt());
    }

    @Test
    @DisplayName("una respuesta vacía no se guarda: la siguiente visita puede reintentar")
    void elFalloNoSeGuarda() {
        catalogo("3000");
        when(ia.json(eq(Comparador.FUNCION), any(), any(), anyInt())).thenReturn(Optional.empty());
        var peticion = new ComparacionRequest(List.of(1L, 2L), "para estudiar");

        comparador.comparar(peticion);
        comparador.comparar(peticion);

        verify(ia, times(2)).json(eq(Comparador.FUNCION), any(), any(), anyInt());
    }

    @Test
    @DisplayName("dos prompts distintos nunca comparten clave")
    void huellaDistinta() {
        assertThat(CacheIa.huella("a", "b")).isNotEqualTo(CacheIa.huella("ab", ""));
        assertThat(CacheIa.huella("a", "b")).isEqualTo(CacheIa.huella("a", "b"));
    }
}
