package Pry_01.Web.de.Ventas.de.Computadoras.Service;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.EnviosDto;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.EnviosModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.PedidoModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.EnviosRepository;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.PedidoRepository;
import jakarta.persistence.EntityNotFoundException;

@Service
public class EnviosService {
    private final EnviosRepository enviosRepository;
    private final PedidoRepository pedidoRepository;

    public EnviosService(EnviosRepository enviosRepository, PedidoRepository pedidoRepository) {
        this.enviosRepository = enviosRepository;
        this.pedidoRepository = pedidoRepository;
    }

    public List<EnviosModel> listarEnvios() {
        return enviosRepository.findAll();
    }

    public void eliminarEnvio(Long id) {
        if (enviosRepository.existsById(id)) {
            enviosRepository.deleteById(id);
        } else {
            throw new EntityNotFoundException("Envio con ID " + id + " no existe");

        }
    }

    public EnviosModel guardarEnvios(EnviosModel envios) {
        if (enviosRepository.existsById(envios.getId())) {
            throw new IllegalArgumentException("El Envío ya existe");
        } else {
            return enviosRepository.save(envios);
        }
    }

    public Optional<EnviosModel> actualizarEnvios(Long id, EnviosDto enviosDto ) {
        Optional<EnviosModel> envioOptional = enviosRepository.findById(id);
        if (!envioOptional.isPresent()) {
            return Optional.empty();
        }
        EnviosModel envio = envioOptional.get();

        if (enviosDto.getDireccion() != null) {
            envio.setDireccion(enviosDto.getDireccion());
        }
        if (enviosDto.getEstadoEnvio() != null) {
            envio.setEstadoEnvio(enviosDto.getEstadoEnvio());
        }
        if (enviosDto.getFechaEnvioProgramado() != null) {
            envio.setFechaEnvioProgramado(enviosDto.getFechaEnvioProgramado());
        }
        if (enviosDto.getFechaEnvioEntregado() != null) {
            envio.setFechaEnvioEntregado(enviosDto.getFechaEnvioEntregado());
        }

        if (enviosDto.getPedidoId() != null) {
            PedidoModel pedido = pedidoRepository.findById(enviosDto.getPedidoId())
                    .orElseThrow(() -> new RuntimeException("envio no encontrado"));
            if (pedido != null) {
                envio.setPedido(pedido);
            }
        }

        EnviosModel EnvioActualizado = enviosRepository.save(envio);

        return Optional.of(EnvioActualizado);
    }

}