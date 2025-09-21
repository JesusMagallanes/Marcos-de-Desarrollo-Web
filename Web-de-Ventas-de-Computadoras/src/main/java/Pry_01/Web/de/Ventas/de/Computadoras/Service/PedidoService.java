package Pry_01.Web.de.Ventas.de.Computadoras.Service;

import java.util.List;
import org.springframework.stereotype.Service;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.PedidoModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.PedidoRepository;
import jakarta.persistence.EntityNotFoundException;

@Service
public class PedidoService {
    private final PedidoRepository pedidoRepository;

    public PedidoService(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
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

    public PedidoModel guardarPedido(PedidoModel pedido){
        if (pedidoRepository.existsById(pedido.getId())) {
            throw new IllegalArgumentException("La producto ya existe");
        }else{
            return pedidoRepository.save(pedido);
        }
    }

}
