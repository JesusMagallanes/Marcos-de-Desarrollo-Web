package com.backend.catalogo.producto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.backend.catalogo.categoria.Categoria;
import com.backend.catalogo.categoria.CategoriaService;
import com.backend.catalogo.marca.MarcaService;
import com.backend.catalogo.producto.dto.ProductoDtos.ProductoRequest;
import com.backend.catalogo.shared.error.ConflictoException;
import com.backend.catalogo.shared.error.RecursoNoEncontradoException;
import com.backend.catalogo.shared.metricas.MetricasSeguridad;
import com.backend.catalogo.valoracion.ValoracionService;

/**
 * Los productos de colaborador (SZ-B08).
 *
 * <p>Lo que se comprueba aquí son las tres reglas que hacen que la moderación
 * sirva de algo: que lo publicado nazca pendiente, que editar lo devuelva a la
 * cola, y que nadie toque lo de otro. Ninguna se ve mirando la entidad, porque
 * viven en el servicio.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Productos de colaborador")
class ProductoColaboradorTest {

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

    @InjectMocks
    private ProductoService servicio;

    private static final Long ANA = 42L;
    private static final Long BETO = 99L;
    private static final Long ADMIN = 1L;

    private ProductoRequest peticion() {
        return new ProductoRequest("Teclado mecánico", "Con switches azules", null,
                new BigDecimal("199.90"), List.of(), 5, 1L, null);
    }

    /** La respuesta lee la categoría, así que el producto de prueba la necesita. */
    private Categoria categoria() {
        Categoria c = new Categoria();
        c.setId(1L);
        c.setName("Teclados");
        return c;
    }

    private Producto deLaTienda() {
        return Producto.builder().id(1L).name("De la tienda").precio(BigDecimal.TEN).stock(5)
                .categoria(categoria()).estadoModeracion(EstadoModeracion.APROBADO).build();
    }

    private Producto deAna(EstadoModeracion estado) {
        return Producto.builder().id(2L).name("De Ana").precio(BigDecimal.TEN).stock(5)
                .categoria(categoria()).propietarioId(ANA).estadoModeracion(estado).build();
    }

    @Nested
    @DisplayName("Al publicar")
    class AlPublicar {

        @Test
        @DisplayName("nace PENDIENTE: nadie publica sin que lo mire alguien")
        void nacePendiente() {
            when(categoriaService.buscar(1L)).thenReturn(new Categoria());
            when(repositorio.countByPropietarioId(ANA)).thenReturn(0L);
            when(repositorio.save(any())).thenAnswer(i -> i.getArgument(0));

            servicio.crearComoColaborador(ANA, peticion());

            org.mockito.ArgumentCaptor<Producto> guardado =
                    org.mockito.ArgumentCaptor.forClass(Producto.class);
            verify(repositorio).save(guardado.capture());

            assertThat(guardado.getValue().getEstadoModeracion())
                    .isEqualTo(EstadoModeracion.PENDIENTE);
            assertThat(guardado.getValue().getPropietarioId()).isEqualTo(ANA);
        }

        @Test
        @DisplayName("hay un tope por colaborador: la cola no es de nadie en exclusiva")
        void topePorColaborador() {
            when(repositorio.countByPropietarioId(ANA)).thenReturn(200L);

            // Sin tope, una cuenta puede llenar la cola de revisión y dejar a los
            // demás sin que nadie les mire nada.
            assertThatThrownBy(() -> servicio.crearComoColaborador(ANA, peticion()))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("máximo");

            verify(repositorio, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Al editar lo propio")
    class AlEditar {

        @Test
        @DisplayName("vuelve a PENDIENTE aunque ya estuviera aprobado")
        void editarDevuelveALaCola() {
            Producto aprobado = deAna(EstadoModeracion.APROBADO);
            when(repositorio.findById(2L)).thenReturn(Optional.of(aprobado));
            when(categoriaService.buscar(1L)).thenReturn(new Categoria());
            when(repositorio.save(any())).thenAnswer(i -> i.getArgument(0));

            servicio.actualizarComoColaborador(2L, ANA, peticion());

            // Si conservara el visto bueno, bastaría con publicar algo inocuo,
            // esperar la aprobación y cambiarlo después por otra cosa.
            assertThat(aprobado.getEstadoModeracion()).isEqualTo(EstadoModeracion.PENDIENTE);
        }

        @Test
        @DisplayName("el producto de otro responde 404, no 403")
        void productoDeOtro() {
            when(repositorio.findById(2L)).thenReturn(Optional.of(deAna(EstadoModeracion.APROBADO)));

            // Un 403 confirmaría que ese id existe, y probando números se sabría
            // qué publica la competencia antes de que se apruebe.
            assertThatThrownBy(() -> servicio.actualizarComoColaborador(2L, BETO, peticion()))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }

        @Test
        @DisplayName("un colaborador no puede tocar lo de la tienda")
        void productoDeLaTienda() {
            when(repositorio.findById(1L)).thenReturn(Optional.of(deLaTienda()));

            assertThatThrownBy(() -> servicio.actualizarComoColaborador(1L, ANA, peticion()))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }
    }

    @Nested
    @DisplayName("Al moderar")
    class AlModerar {

        @Test
        @DisplayName("aprobar publica el producto y lo cuenta")
        void aprobar() {
            Producto pendiente = deAna(EstadoModeracion.PENDIENTE);
            when(repositorio.findById(2L)).thenReturn(Optional.of(pendiente));
            when(repositorio.save(any())).thenAnswer(i -> i.getArgument(0));

            servicio.aprobarModeracion(2L, ADMIN);

            assertThat(pendiente.getEstadoModeracion()).isEqualTo(EstadoModeracion.APROBADO);
            assertThat(pendiente.getModeradoPor()).isEqualTo(ADMIN);
            verify(metricas).moderacionProducto("aprobado");
        }

        @Test
        @DisplayName("rechazar guarda el motivo, que es lo que el vendedor va a leer")
        void rechazar() {
            Producto pendiente = deAna(EstadoModeracion.PENDIENTE);
            when(repositorio.findById(2L)).thenReturn(Optional.of(pendiente));
            when(repositorio.save(any())).thenAnswer(i -> i.getArgument(0));

            servicio.rechazarModeracion(2L, ADMIN, "Las fotos no son del producto que describe.");

            assertThat(pendiente.getEstadoModeracion()).isEqualTo(EstadoModeracion.RECHAZADO);
            assertThat(pendiente.getMotivoRechazo()).contains("no son del producto");
            verify(metricas).moderacionProducto("rechazado");
        }

        @Test
        @DisplayName("lo de la tienda no pasa por moderación")
        void tiendaNoSeModera() {
            when(repositorio.findById(1L)).thenReturn(Optional.of(deLaTienda()));

            // Permitirlo sería que el administrador se aprobara a sí mismo, y el
            // estado dejaría de significar nada.
            assertThatThrownBy(() -> servicio.aprobarModeracion(1L, ADMIN))
                    .isInstanceOf(ConflictoException.class)
                    .hasMessageContaining("no pasan por moderación");

            verify(metricas, never()).moderacionProducto(any());
        }
    }
}
