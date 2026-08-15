package com.backend.catalogo.valoracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.backend.catalogo.producto.Producto;
import com.backend.catalogo.producto.ProductoRepository;
import com.backend.catalogo.shared.compras.ComprasClient;
import com.backend.catalogo.shared.error.ConflictoException;
import com.backend.catalogo.valoracion.dto.ValoracionDtos.ValoracionRequest;

/**
 * Solo valora quien compro.
 *
 * <p>Sin esta regla, cualquiera con una cuenta podia puntuar cualquier producto
 * sin haberlo comprado nunca: es como se llenan de resenias falsas las tiendas,
 * tanto para inflar lo propio como para hundir lo ajeno.
 *
 * <p>Se comprueba en el servidor y no escondiendo el boton, porque el boton se
 * salta: la peticion sale igual desde la consola del navegador.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Solo quien compro puede valorar")
class SoloComproPuedeValorarTest {

    @Mock
    private ValoracionRepository repositorio;
    @Mock
    private ProductoRepository productoRepository;
    @Mock
    private ComprasClient compras;

    @InjectMocks
    private ValoracionService servicio;

    private static final Long PRODUCTO = 7L;
    private static final Long ANA = 42L;
    private static final String TOKEN = "token-de-ana";

    private Producto producto() {
        return Producto.builder().id(PRODUCTO).name("Teclado").precio(BigDecimal.TEN).stock(3).build();
    }

    private ValoracionRequest peticion() {
        return new ValoracionRequest(5, "Muy buen teclado, lo uso a diario.", "Ana");
    }

    @Test
    @DisplayName("quien NO compro recibe un 409 que le explica por que")
    void sinCompraNoValora() {
        when(productoRepository.findById(PRODUCTO)).thenReturn(Optional.of(producto()));
        when(compras.comproElProducto(TOKEN, PRODUCTO)).thenReturn(false);

        assertThatThrownBy(() -> servicio.guardar(PRODUCTO, ANA, peticion(), TOKEN))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("productos que hayas comprado");

        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("quien compro si valora, y queda pendiente de moderacion")
    void conCompraSiValora() {
        when(productoRepository.findById(PRODUCTO)).thenReturn(Optional.of(producto()));
        when(compras.comproElProducto(TOKEN, PRODUCTO)).thenReturn(true);
        when(repositorio.findByProductoIdAndUsuarioId(PRODUCTO, ANA)).thenReturn(Optional.empty());
        when(repositorio.save(any())).thenAnswer(i -> i.getArgument(0));

        var guardada = servicio.guardar(PRODUCTO, ANA, peticion(), TOKEN);

        // Comprar da derecho a opinar, no a publicar sin revision.
        assertThat(guardada.estado()).isEqualTo(EstadoValoracion.PENDIENTE);
    }

    @Test
    @DisplayName("si `compras` no responde, NO se deja valorar")
    void anteLaDudaNoSeDeja() {
        when(productoRepository.findById(PRODUCTO)).thenReturn(Optional.of(producto()));
        // El cliente devuelve false cuando la llamada falla: no se puede saber si
        // hubo compra, y dar por buena una resenia sin comprobar es justo lo que
        // se quiere evitar.
        when(compras.comproElProducto(anyString(), anyLong())).thenReturn(false);

        assertThatThrownBy(() -> servicio.guardar(PRODUCTO, ANA, peticion(), TOKEN))
                .isInstanceOf(ConflictoException.class);
    }

    @Test
    @DisplayName("sin token tampoco: no hay a quien atribuir la compra")
    void sinToken() {
        when(productoRepository.findById(PRODUCTO)).thenReturn(Optional.of(producto()));
        when(compras.comproElProducto(null, PRODUCTO)).thenReturn(false);

        assertThatThrownBy(() -> servicio.guardar(PRODUCTO, ANA, peticion(), null))
                .isInstanceOf(ConflictoException.class);
    }

    @Test
    @DisplayName("se comprueba la compra ANTES de tocar la base")
    void seComprubaAntesDeGuardar() {
        when(productoRepository.findById(PRODUCTO)).thenReturn(Optional.of(producto()));
        when(compras.comproElProducto(TOKEN, PRODUCTO)).thenReturn(false);

        assertThatThrownBy(() -> servicio.guardar(PRODUCTO, ANA, peticion(), TOKEN))
                .isInstanceOf(ConflictoException.class);

        // Ni se busca la valoracion previa: si no compro, no hay nada que mirar.
        verify(repositorio, never()).findByProductoIdAndUsuarioId(anyLong(), anyLong());
    }
}
