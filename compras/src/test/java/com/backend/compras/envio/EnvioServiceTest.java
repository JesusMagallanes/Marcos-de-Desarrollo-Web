package com.backend.compras.envio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.backend.compras.pedido.Pedido;
import com.backend.compras.shared.error.ConflictoException;
import com.backend.compras.shared.error.RecursoNoEncontradoException;

@ExtendWith(MockitoExtension.class)
@DisplayName("EnvioService - cambio de estado")
class EnvioServiceTest {

    @Mock
    private EnvioRepository repositorio;

    @InjectMocks
    private EnvioService servicio;

    private Envio envioConEstado(EstadoEnvio estado) {
        Pedido pedido = Pedido.builder().id(10L).build();
        return Envio.builder()
                .id(1L).direccion("Av Lima 123").estadoEnvio(estado).pedido(pedido)
                .build();
    }

    @Test
    @DisplayName("pendiente -> en tránsito es válido")
    void pendienteAEnTransito() {
        Envio envio = envioConEstado(EstadoEnvio.PENDIENTE);
        when(repositorio.findById(1L)).thenReturn(Optional.of(envio));
        when(repositorio.save(any())).thenReturn(envio);

        servicio.cambiarEstado(1L, EstadoEnvio.EN_TRANSITO);

        ArgumentCaptor<Envio> captor = ArgumentCaptor.forClass(Envio.class);
        verify(repositorio).save(captor.capture());
        assertThat(captor.getValue().getEstadoEnvio()).isEqualTo(EstadoEnvio.EN_TRANSITO);
    }

    @Test
    @DisplayName("en tránsito -> entregado es válido")
    void enTransitoAEntregado() {
        Envio envio = envioConEstado(EstadoEnvio.EN_TRANSITO);
        when(repositorio.findById(1L)).thenReturn(Optional.of(envio));
        when(repositorio.save(any())).thenReturn(envio);

        servicio.cambiarEstado(1L, EstadoEnvio.ENTREGADO);

        ArgumentCaptor<Envio> captor = ArgumentCaptor.forClass(Envio.class);
        verify(repositorio).save(captor.capture());
        assertThat(captor.getValue().getEstadoEnvio()).isEqualTo(EstadoEnvio.ENTREGADO);
    }

    @Test
    @DisplayName("entregado -> pendiente está prohibido")
    void entregadoAPendienteProhibido() {
        Envio envio = envioConEstado(EstadoEnvio.ENTREGADO);
        when(repositorio.findById(1L)).thenReturn(Optional.of(envio));

        assertThatThrownBy(() -> servicio.cambiarEstado(1L, EstadoEnvio.PENDIENTE))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("ENTREGADO")
                .hasMessageContaining("PENDIENTE");

        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("entregado -> en tránsito está prohibido")
    void entregadoAEnTransitoProhibido() {
        Envio envio = envioConEstado(EstadoEnvio.ENTREGADO);
        when(repositorio.findById(1L)).thenReturn(Optional.of(envio));

        assertThatThrownBy(() -> servicio.cambiarEstado(1L, EstadoEnvio.EN_TRANSITO))
                .isInstanceOf(ConflictoException.class);

        verify(repositorio, never()).save(any());
    }

    @Test
    @DisplayName("envío inexistente da 404")
    void envioInexistente() {
        when(repositorio.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.cambiarEstado(99L, EstadoEnvio.EN_TRANSITO))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
