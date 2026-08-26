package com.backend.catalogo.producto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.backend.catalogo.categoria.Categoria;
import com.backend.catalogo.categoria.CategoriaService;
import com.backend.catalogo.categoria.dto.CategoriaDtos.CategoriaResponse;
import com.backend.catalogo.marca.Marca;
import com.backend.catalogo.marca.MarcaService;
import com.backend.catalogo.producto.dto.ProductoDtos.PaginaResponse;
import com.backend.catalogo.producto.dto.ProductoDtos.ProductoResponse;
import com.backend.catalogo.shared.metricas.MetricasSeguridad;
import com.backend.catalogo.valoracion.ValoracionService;

/**
 * Que la vitrina se sirva por páginas y acotada.
 *
 * <p>El listado público devolvía una lista con TODO el catálogo aprobado, en el
 * endpoint más visitado de la tienda, y la portada se lo descargaba entero para
 * enseñar unas decenas de productos: diez destacados, los que estuvieran en
 * oferta y doce por categoría. La respuesta crecía con la tienda aunque la
 * pantalla que la consumía fuera siempre la misma.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Vitrina paginada y portada")
class PortadaYPaginacionTest {

    @Mock
    private ProductoRepository repositorio;
    @Mock
    private CategoriaService categoriaService;
    @Mock
    private MarcaService marcaService;
    @Mock
    private ValoracionService valoracionService;
    @Mock
    private MetricasSeguridad metricas;

    private ProductoService servicio;

    @BeforeEach
    void preparar() {
        servicio = new ProductoService(repositorio, categoriaService, marcaService,
                valoracionService, metricas);
        lenient().when(valoracionService.resumenPorProductos(anyList())).thenReturn(Map.of());
    }

    private Producto producto(long id, String nombre) {
        // Categoría y marca van pobladas porque la respuesta las lee: las
        // consultas de listado hacen JOIN FETCH de las dos, justamente para no
        // disparar el N+1 que tenía la portada del monolito.
        return Producto.builder()
                .id(id).name(nombre).description("d")
                .precio(new BigDecimal("100.00")).stock(5)
                .estadoModeracion(EstadoModeracion.APROBADO)
                .categoria(Categoria.builder().id(7L).name("Monitores").slug("monitores").build())
                .marca(Marca.builder().id(3L).name("LG").build())
                .imagenes(List.of())
                .build();
    }

    /* ══════════════ Paginación ══════════════ */

    @Test
    @DisplayName("listar() pide una página al repositorio y devuelve sus totales")
    void listarPagina() {
        Page<Producto> pagina = new PageImpl<>(
                List.of(producto(1, "Monitor")), PageRequest.of(2, 12), 120);
        when(repositorio.listarConRelaciones(any(Pageable.class))).thenReturn(pagina);

        PaginaResponse<ProductoResponse> respuesta = servicio.listar(null, 2, 12);

        ArgumentCaptor<Pageable> pedido = ArgumentCaptor.forClass(Pageable.class);
        verify(repositorio).listarConRelaciones(pedido.capture());
        assertThat(pedido.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pedido.getValue().getPageSize()).isEqualTo(12);

        // Los totales son los de la consulta entera, no los de la página: sin
        // ellos el navegador no sabe si hay más ni cuántas páginas pintar.
        assertThat(respuesta.totalElements()).isEqualTo(120);
        assertThat(respuesta.number()).isEqualTo(2);
        assertThat(respuesta.content()).hasSize(1);
    }

    @Test
    @DisplayName("con búsqueda va por la consulta de texto, también paginada")
    void buscarPaginado() {
        when(repositorio.buscarPorTexto(eq("monitor"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(producto(1, "Monitor")), PageRequest.of(0, 12), 1));

        servicio.listar("monitor", 0, 12);

        verify(repositorio).buscarPorTexto(eq("monitor"), any(Pageable.class));
        verify(repositorio, never()).listarConRelaciones(any(Pageable.class));
    }

    /* ══════════════ Portada ══════════════ */

    @Test
    @DisplayName("la portada resuelve sus tres listas SIN traerse el catálogo")
    void portadaAcotada() {
        when(repositorio.listarConRelaciones(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(producto(1, "Monitor"))));
        when(repositorio.listarEnOferta(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(producto(2, "Teclado")));
        when(categoriaService.listar()).thenReturn(
                List.of(new CategoriaResponse(7L, "Monitores", "monitores", "d", null)));
        when(repositorio.listarDeCategoria(eq(7L), any(Pageable.class)))
                .thenReturn(List.of(producto(1, "Monitor")));

        var portada = servicio.portada();

        assertThat(portada.destacados()).hasSize(1);
        assertThat(portada.ofertas()).hasSize(1);
        assertThat(portada.porCategoria()).hasSize(1);
        assertThat(portada.porCategoria().get(0).categoria().name()).isEqualTo("Monitores");

        // Lo que importa: TODAS las consultas van acotadas. Si alguna dejara de
        // llevar `Pageable`, volveríamos a descargar la tienda entera.
        ArgumentCaptor<Pageable> destacados = ArgumentCaptor.forClass(Pageable.class);
        verify(repositorio).listarConRelaciones(destacados.capture());
        assertThat(destacados.getValue().getPageSize()).isEqualTo(10);

        ArgumentCaptor<Pageable> porCategoria = ArgumentCaptor.forClass(Pageable.class);
        verify(repositorio).listarDeCategoria(eq(7L), porCategoria.capture());
        assertThat(porCategoria.getValue().getPageSize()).isEqualTo(12);
    }

    @Test
    @DisplayName("una categoría sin productos no pinta un bloque vacío")
    void categoriaVaciaNoSaleEnLaPortada() {
        when(repositorio.listarConRelaciones(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(repositorio.listarEnOferta(any(Instant.class), any(Pageable.class))).thenReturn(List.of());
        when(categoriaService.listar()).thenReturn(
                List.of(new CategoriaResponse(9L, "Sin nada", "sin-nada", "d", null)));
        when(repositorio.listarDeCategoria(eq(9L), any(Pageable.class))).thenReturn(List.of());

        assertThat(servicio.portada().porCategoria()).isEmpty();
    }

    /* ══════════════ Ofertas ══════════════ */

    @Test
    @DisplayName("las ofertas salen de una consulta acotada, no de filtrar el catálogo")
    void ofertasAcotadas() {
        when(repositorio.listarEnOferta(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(producto(2, "Teclado")));

        assertThat(servicio.ofertas(5)).hasSize(1);

        ArgumentCaptor<Pageable> limite = ArgumentCaptor.forClass(Pageable.class);
        verify(repositorio).listarEnOferta(any(Instant.class), limite.capture());
        assertThat(limite.getValue().getPageSize()).isEqualTo(5);

        // El chatbot y la portada pedían el catálogo entero para quedarse con
        // los que estuvieran en oferta, que suelen ser un puñado.
        verify(repositorio, never()).listarConRelaciones(any(Pageable.class));
    }
}
