package com.backend.compras.pedido;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.backend.compras.envio.Envio;
import com.backend.compras.envio.EnvioRepository;
import com.backend.compras.metodopago.MetodoPagoRepository;
import com.backend.compras.shared.error.ConflictoException;
import com.backend.compras.shared.error.RecursoNoEncontradoException;

@ExtendWith(MockitoExtension.class)
@DisplayName("PedidoService - eliminar")
class PedidoServiceEliminarTest {

    @Mock
    private PedidoRepository pedidoRepositorio;
    @Mock
    private MetodoPagoRepository metodoPagoRepositorio;
    @Mock
    private EnvioRepository envioRepositorio;

    @InjectMocks
    private PedidoService servicio;

    @Test
    @DisplayName("un pedido sin envío se puede eliminar")
    void eliminarSinEnvio() {
        Pedido pedido = Pedido.builder().id(1L).usuarioId(1L).build();
        when(pedidoRepositorio.findById(1L)).thenReturn(Optional.of(pedido));
        when(envioRepositorio.findByPedidoId(1L)).thenReturn(Optional.empty());

        servicio.eliminar(1L);

        verify(pedidoRepositorio).delete(pedido);
    }

    @Test
    @DisplayName("un pedido con envío NO se puede eliminar")
    void eliminarConEnvio() {
        Pedido pedido = Pedido.builder().id(1L).usuarioId(1L).build();
        Envio envio = Envio.builder().id(1L).build();
        when(pedidoRepositorio.findById(1L)).thenReturn(Optional.of(pedido));
        when(envioRepositorio.findByPedidoId(1L)).thenReturn(Optional.of(envio));

        assertThatThrownBy(() -> servicio.eliminar(1L))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("envío");

        verify(pedidoRepositorio, never()).delete(any());
    }

    @Test
    @DisplayName("eliminar un pedido inexistente da 404")
    void eliminarInexistente() {
        when(pedidoRepositorio.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.eliminar(99L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
