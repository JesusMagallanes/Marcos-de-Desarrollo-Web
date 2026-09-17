package com.backend.catalogo.descubrimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.backend.catalogo.categoria.CategoriaService;
import com.backend.catalogo.categoria.dto.CategoriaDtos.CategoriaResponse;
import com.backend.catalogo.producto.EstadoModeracion;
import com.backend.catalogo.producto.ProductoService;
import com.backend.catalogo.producto.dto.ProductoDtos.AtributoValor;
import com.backend.catalogo.producto.dto.ProductoDtos.ProductoResponse;
import com.backend.catalogo.shared.ia.ClienteIa;
import com.backend.catalogo.shared.ia.GuardiaPrompt;

import tools.jackson.databind.json.JsonMapper;

@DisplayName("¿Por qué veo esto?")
class ExplicadorRecomendacionTest {

    private final UUID sujeto = UUID.randomUUID();
    private PerfilService perfiles;
    private ClienteIa ia;
    private ExplicadorRecomendacion explicador;

    @BeforeEach
    void preparar() {
        perfiles = mock(PerfilService.class);
        ia = mock(ClienteIa.class);
        ProductoService productos = mock(ProductoService.class);
        CategoriaService categorias = mock(CategoriaService.class);
        when(productos.obtener(3L)).thenReturn(new ProductoResponse(3L, "ASUS TUF", "d", null, BigDecimal.TEN,
                null, null, null, null, null, BigDecimal.TEN, false, null, null, null, List.of(), 2, 1L, "Laptops",
                2L, "ASUS", null, EstadoModeracion.APROBADO, null,
                List.of(new AtributoValor("ram", "Memoria RAM", "16 GB", null, null))));
        when(categorias.obtener(1L)).thenReturn(new CategoriaResponse(1L, "Laptops", "laptops", "", "", null));
        explicador = new ExplicadorRecomendacion(perfiles, productos, categorias, ia, new GuardiaPrompt());
    }

    private PerfilFaceta faceta(TipoFaceta tipo, String valor, double score) {
        PerfilFaceta f = new PerfilFaceta();
        f.setId(new PerfilFaceta.Id(sujeto, tipo, valor));
        f.setScore(BigDecimal.valueOf(score));
        return f;
    }

    @Test
    @DisplayName("las señales son coincidencias reales del propio perfil, de más a menos peso")
    void senales() {
        when(perfiles.completo(sujeto)).thenReturn(List.of(
                faceta(TipoFaceta.MARCA, "Lenovo", 9),
                faceta(TipoFaceta.ATRIBUTO, "ram=16 GB", 5),
                faceta(TipoFaceta.CATEGORIA, "laptops", 7)));

        var r = explicador.explicar(sujeto, 3L, "SEGUN_TUS_INTERESES");

        assertThat(r.senales()).containsExactly("Categoría: Laptops", "Característica: Memoria RAM 16 GB");
        assertThat(r.explicacion()).isEqualTo("Porque te has interesado por Laptops, Memoria RAM 16 GB.");
        assertThat(r.conIa()).isFalse();
    }

    @Test
    @DisplayName("en el módulo colaborativo nunca hay detalles: hablarían de otras personas")
    void colaborativoGenerico() {
        when(ia.disponible()).thenReturn(true);

        var r = explicador.explicar(sujeto, 3L, "OTROS_DESCUBRIERON");

        assertThat(r.senales()).isEmpty();
        verify(perfiles, never()).completo(any());
        verify(ia, never()).json(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("con IA, el modelo redacta; si devuelve algo inservible, queda la plantilla")
    void conIa() {
        when(ia.disponible()).thenReturn(true);
        when(perfiles.completo(sujeto)).thenReturn(List.of(faceta(TipoFaceta.MARCA, "asus", 3)));
        when(ia.json(any(), any(), any(), anyInt())).thenReturn(Optional.of(
                JsonMapper.builder().build().readTree("{\"explicacion\":\"Te gusta ASUS, y esta es de ASUS.\"}")));

        var r = explicador.explicar(sujeto, 3L, "RELACIONADOS");

        assertThat(r.conIa()).isTrue();
        assertThat(r.explicacion()).isEqualTo("Te gusta ASUS, y esta es de ASUS.");
    }
}
