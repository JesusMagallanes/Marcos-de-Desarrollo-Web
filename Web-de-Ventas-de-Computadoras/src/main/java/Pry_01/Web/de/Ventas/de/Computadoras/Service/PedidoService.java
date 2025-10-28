

/*package Pry_01.Web.de.Ventas.de.Computadoras.Service;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.MetodoPagoModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.PedidoModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.MetodoPagoRepository;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.PedidoRepository;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;

@Service
public class PedidoService {
    private final PedidoRepository pedidoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MetodoPagoRepository metodoPagoRepository;

    public PedidoService(PedidoRepository pedidoRepository, UsuarioRepository usuarioRepository,
            MetodoPagoRepository metodoPagoRepository) {
        this.pedidoRepository = pedidoRepository;
        this.usuarioRepository = usuarioRepository;
        this.metodoPagoRepository = metodoPagoRepository;
    }

    public List<PedidoModel> listarPedido() {
        return pedidoRepository.findAll();
    }

    public void eliminarPedidoPorId(Long id) {
        if (pedidoRepository.existsById(id)) {
            pedidoRepository.deleteById(id);
        } else {
            throw new EntityNotFoundException("Pedido con ID " + id + " no existe");
        }
    }

    public PedidoModel guardarPedido(PedidoModel pedido) {
        if (pedidoRepository.existsById(pedido.getId())) {
            throw new IllegalArgumentException("La producto ya existe");
        } else {
            return pedidoRepository.save(pedido);
        }
    }

    public Optional<PedidoModel> actualizarPedido(Long id, PedidoDto pedidoDto) {
        Optional<PedidoModel> pedidoOptional = pedidoRepository.findById(id);
        if (!pedidoOptional.isPresent()) {
            return Optional.empty();
        }
        PedidoModel pedido = pedidoOptional.get();

        if (pedidoDto.getEstado() != null) {
            pedido.setEstado(pedidoDto.getEstado());

        }
        if (pedidoDto.getUsuarioId() != null) {
            UsuarioModel usuario = usuarioRepository.findById(pedidoDto.getUsuarioId())
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
            pedido.setUsuario(usuario);
        }
        if (pedidoDto.getMetodoPagoId() != null) {
            MetodoPagoModel metodoPago = metodoPagoRepository.findById(pedidoDto.getMetodoPagoId())
                    .orElseThrow(() -> new RuntimeException("Método no encontrado"));
            pedido.setMetodoPago(metodoPago);
        }
        if (pedidoDto.getTotal() != null) {
            pedido.setTotal(pedidoDto.getTotal());
        }
        PedidoModel pedidoActualizado = pedidoRepository.save(pedido);
        return Optional.of(pedidoActualizado);
    }
}
*/