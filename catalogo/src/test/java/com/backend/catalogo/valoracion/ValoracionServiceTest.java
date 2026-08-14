package com.backend.catalogo.valoracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.backend.catalogo.producto.Producto;
import com.backend.catalogo.producto.ProductoRepository;
import com.backend.catalogo.valoracion.dto.ValoracionDtos.ValoracionDestacadaResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("Top de valoraciones de la portada")
class ValoracionServiceTest {

    @Mock
    private ValoracionRepository repositorio;
    @Mock
    private ProductoRepository productoRepository;
    @InjectMocks
    private ValoracionService servicio;

    @Test
    @DisplayName("pide solo las aprobadas, nunca las pendientes o rechazadas")
    void soloAprobadas() {
        when(repositorio.findTop6ByEstadoOrderByCalificacionDescCreadoEnDesc(EstadoValoracion.APROBADA))
                .thenReturn(List.of());

        servicio.destacadas();

        verify(repositorio).findTop6ByEstadoOrderByCalificacionDescCreadoEnDesc(EstadoValoracion.APROBADA);
    }

    @Test
    @DisplayName("cada reseña viaja con su producto para que la portada sepa a qué va")
    void mapeaProducto() {
        Producto producto = Producto.builder()
                .id(7L)
                .name("Asus ROG Strix G16")
                .imageUrl("https://img.example/rog-g16.jpg")
                .build();
        Valoracion valoracion = Valoracion.builder()
                .id(20L)
                .producto(producto)
                .nombre("Pamela")
                .calificacion(5)
                .comentario("Excelente portátil")
                .estado(EstadoValoracion.APROBADA)
                .build();
        when(repositorio.findTop6ByEstadoOrderByCalificacionDescCreadoEnDesc(EstadoValoracion.APROBADA))
                .thenReturn(List.of(valoracion));

        List<ValoracionDestacadaResponse> respuesta = servicio.destacadas();

        assertThat(respuesta).hasSize(1);
        ValoracionDestacadaResponse dto = respuesta.get(0);
        assertThat(dto.productoId()).isEqualTo(7L);
        assertThat(dto.productoNombre()).isEqualTo("Asus ROG Strix G16");
        assertThat(dto.productoImagenUrl()).isEqualTo("https://img.example/rog-g16.jpg");
        assertThat(dto.nombre()).isEqualTo("Pamela");
        assertThat(dto.calificacion()).isEqualTo(5);
    }
}
