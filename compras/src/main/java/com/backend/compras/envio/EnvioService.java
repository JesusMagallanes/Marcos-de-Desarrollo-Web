package com.backend.compras.envio;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.compras.envio.dto.EnvioDtos.EnvioResponse;
import com.backend.compras.pedido.Pedido;
import com.backend.compras.shared.error.RecursoNoEncontradoException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class EnvioService {

    private final EnvioRepository repositorio;

    public List<EnvioResponse> listar(EstadoEnvio estado) {
        List<Envio> envios = estado == null
                ? repositorio.findAll()
                : repositorio.findByEstadoEnvioOrderByIdDesc(estado);
        return envios.stream().map(EnvioResponse::desde).toList();
    }

    public List<EnvioResponse> misEnvios(Long usuarioId) {
        return repositorio.listarPorUsuario(usuarioId).stream().map(EnvioResponse::desde).toList();
    }

    /** Se crea al confirmarse el pago; la dirección no se pide de nuevo. */
    @Transactional
    public Envio crearParaPedido(Pedido pedido) {
        return repositorio.findByPedidoId(pedido.getId()).orElseGet(() -> {
            Envio envio = Envio.builder()
                    .pedido(pedido)
                    .direccion("Por confirmar")
                    .estadoEnvio(EstadoEnvio.PENDIENTE)
                    .build();
            log.info("Envío creado para el pedido {}", pedido.getId());
            return repositorio.save(envio);
        });
    }

    @Transactional
    public EnvioResponse cambiarEstado(Long id, EstadoEnvio nuevoEstado) {
        Envio envio = repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Envío " + id + " no encontrado"));

        switch (nuevoEstado) {
            case EN_TRANSITO -> envio.marcarEnTransito();
            case ENTREGADO -> envio.marcarEntregado();
            case PENDIENTE -> envio.setEstadoEnvio(EstadoEnvio.PENDIENTE);
        }

        return EnvioResponse.desde(repositorio.save(envio));
    }
}
