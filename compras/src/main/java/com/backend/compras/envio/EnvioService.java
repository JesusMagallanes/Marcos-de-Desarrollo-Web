package com.backend.compras.envio;

import java.util.List;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.compras.envio.dto.EnvioDtos.EnvioResponse;
import com.backend.compras.pedido.Pedido;
import com.backend.compras.saga.SagaCheckout;
import com.backend.compras.shared.error.RecursoNoEncontradoException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class EnvioService {

    private final EnvioRepository repositorio;

    /*
     * Desde dónde salen los repartos. Se configura porque una tienda se muda y
     * dejarlo escrito en el código obligaría a recompilar; el valor por defecto
     * es el centro de Lima, que sirve para desarrollo.
     */
    @Value("${compras.tienda.latitud:-12.046374}")
    private BigDecimal tiendaLat;

    @Value("${compras.tienda.longitud:-77.042793}")
    private BigDecimal tiendaLon;

    public List<EnvioResponse> listar(EstadoEnvio estado) {
        List<Envio> envios = estado == null
                ? repositorio.findAll()
                : repositorio.findByEstadoEnvioOrderByIdDesc(estado);
        // La lista del panel SÍ lleva la distancia: es la pantalla desde la que
        // se organizan los repartos.
        return envios.stream().map(this::conDistancia).toList();
    }

    public List<EnvioResponse> misEnvios(Long usuarioId) {
        // Al comprador no se le calcula la distancia: es información para quien
        // reparte, y a él solo le diría a qué distancia está de la tienda un
        // paquete que ya pidió.
        return repositorio.listarPorUsuario(usuarioId).stream().map(EnvioResponse::desde).toList();
    }

    /**
     * Con la distancia desde la tienda: es lo que necesita quien organiza los
     * repartos para agruparlos por zona y decidir qué sale antes.
     */
    public EnvioResponse conDistancia(Envio envio) {
        return EnvioResponse.desde(envio,
                Distancia.desdeLaTienda(tiendaLat, tiendaLon, envio.getLatitud(), envio.getLongitud()));
    }

    /**
     * Se crea al confirmarse el pago, con el destino que el comprador eligió al
     * iniciar el checkout. No se le vuelve a preguntar: en ese momento ya no
     * está en la tienda, y antes esto ponía la cadena literal "Por confirmar",
     * con lo que cada pedido pagado era un pedido que no se podía entregar.
     */
    @Transactional
    public Envio crearParaPedido(Pedido pedido, SagaCheckout saga) {
        return repositorio.findByPedidoId(pedido.getId()).orElseGet(() -> {
            Envio envio = Envio.builder()
                    .pedido(pedido)
                    .direccion(saga.getDireccionEnvio())
                    .referencia(saga.getReferenciaEnvio())
                    .telefonoContacto(saga.getTelefonoContacto())
                    .latitud(saga.getLatitud())
                    .longitud(saga.getLongitud())
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
