package Pry_01.Web.de.Ventas.de.Computadoras.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.EnviosModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.EstadoEnvio;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.PedidoModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.EnvioRepository;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.PedidoRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class EnvioService {

    @Autowired
    private EnvioRepository envioRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    public List<EnviosModel> obtenerTodosLosEnvios() {
        return envioRepository.findAll();
    }

    public List<EnviosModel> obtenerEnviosPendientesYEnCamino() {
        return envioRepository.findByEstadoEnvioIn(
                Arrays.asList(EstadoEnvio.PENDIENTE, EstadoEnvio.EN_TRANSITO));
    }

    public List<EnviosModel> obtenerEnviosPorEstado(EstadoEnvio estado) {
        return envioRepository.findByEstadoEnvio(estado);
    }

    public Optional<EnviosModel> obtenerEnvioPorId(Long id) {
        return envioRepository.findById(id);
    }

    public Optional<EnviosModel> obtenerEnvioPorPedidoId(Long pedidoId) {
        return envioRepository.findByPedidoId(pedidoId);
    }

    @Transactional
    public EnviosModel crearEnvio(Long pedidoId) {
        Optional<PedidoModel> pedidoOpt = pedidoRepository.findById(pedidoId);

        if (pedidoOpt.isEmpty()) {
            throw new RuntimeException("Pedido no encontrado con ID: " + pedidoId);
        }

        Optional<EnviosModel> envioExistente = envioRepository.findByPedidoId(pedidoId);
        if (envioExistente.isPresent()) {
            return envioExistente.get();
        }

        EnviosModel envio = new EnviosModel();
        envio.setPedido(pedidoOpt.get());

        // Manejo robusto de dirección
        String direccion = pedidoOpt.get().getUsuario().getAddress();
        if (direccion == null || direccion.trim().isEmpty()) {
            direccion = "Dirección no registrada";
        }
        envio.setDireccion(direccion);

        envio.setEstadoEnvio(EstadoEnvio.PENDIENTE);

        // Establecer fecha por defecto para evitar error de restricción NOT NULL en BD
        envio.setFechaEnvioProgramado(LocalDateTime.now().plusDays(3));

        return envioRepository.save(envio);
    }

    @Transactional
    public EnviosModel programarFechaEnvio(Long envioId, LocalDateTime fechaEnvio) {
        EnviosModel envio = envioRepository.findById(envioId)
                .orElseThrow(() -> new RuntimeException("Envío no encontrado con ID: " + envioId));

        envio.setFechaEnvioProgramado(fechaEnvio);
        return envioRepository.save(envio);
    }

    @Transactional
    public EnviosModel actualizarEstado(Long envioId, EstadoEnvio nuevoEstado) {
        Optional<EnviosModel> envioOpt = envioRepository.findById(envioId);

        if (envioOpt.isEmpty()) {
            throw new RuntimeException("Envío no encontrado con ID: " + envioId);
        }

        EnviosModel envio = envioOpt.get();
        envio.setEstadoEnvio(nuevoEstado);

        if (nuevoEstado == EstadoEnvio.ENTREGADO) {
            envio.setFechaEnvioEntregado(LocalDateTime.now());
        }

        return envioRepository.save(envio);
    }

    public List<EnviosModel> obtenerEnviosPorUsuario(Long userId) {
        return envioRepository.findByUsuarioId(userId);
    }

    public Long contarEnviosPorEstado(EstadoEnvio estado) {
        return envioRepository.countByEstadoEnvio(estado);
    }

    @Transactional
    public void crearEnviosParaPedidosSinEnvio() {
        List<PedidoModel> pedidos = pedidoRepository.findAll();

        for (PedidoModel pedido : pedidos) {
            Optional<EnviosModel> envioExistente = envioRepository.findByPedidoId(pedido.getId());
            if (envioExistente.isEmpty()) {
                EnviosModel envio = new EnviosModel();
                envio.setPedido(pedido);

                String direccion = pedido.getUsuario().getAddress();
                if (direccion == null || direccion.trim().isEmpty()) {
                    direccion = "Dirección no registrada";
                }
                envio.setDireccion(direccion);

                envio.setEstadoEnvio(EstadoEnvio.PENDIENTE);
                envio.setFechaEnvioProgramado(LocalDateTime.now().plusDays(3));

                envioRepository.save(envio);
            }
        }
    }
}