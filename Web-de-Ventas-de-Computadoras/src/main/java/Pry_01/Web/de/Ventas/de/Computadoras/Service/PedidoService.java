package Pry_01.Web.de.Ventas.de.Computadoras.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.CarritoItemModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.CarritoModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.DetallePedidoModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.EstadoPedido;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.MetodoPagoModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.PedidoModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.MetodoPagoRepository;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.PedidoRepository;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MetodoPagoRepository metodoPagoRepository;
    private final CarritoService carritoService;

    public PedidoService(PedidoRepository pedidoRepository, 
                         UsuarioRepository usuarioRepository,
                         MetodoPagoRepository metodoPagoRepository, 
                         CarritoService carritoService) {
        this.pedidoRepository = pedidoRepository;
        this.usuarioRepository = usuarioRepository;
        this.metodoPagoRepository = metodoPagoRepository;
        this.carritoService = carritoService;
    }

    public List<PedidoModel> listarPedido() {
        return pedidoRepository.findAll();
    }

    public List<PedidoModel> listarPedidosPorUsuario(UsuarioModel usuario) {
        return pedidoRepository.findByUsuarioOrderByCreadoEnDesc(usuario);
    }

    public void eliminarPedidoPorId(Long id) {
        if (!pedidoRepository.existsById(id)) {
            throw new EntityNotFoundException("Pedido con ID " + id + " no existe");
        }
        pedidoRepository.deleteById(id);
    }

    public PedidoModel guardarPedido(PedidoModel pedido) {
        return pedidoRepository.save(pedido);
    }

    /**
     * Crea un pedido desde el carrito actual del usuario
     */
    public PedidoModel crearPedidoDesdeCarrito(UsuarioModel usuario, Long metodoPagoId) {

        log.info("Iniciando creación de pedido desde carrito para usuario ID {}", usuario.getId());

        // Obtener carrito
        CarritoModel carrito = carritoService.obtenerCarrito(usuario);
        if (carrito.getItems().isEmpty()) {
            log.warn("El carrito del usuario ID {} está vacío", usuario.getId());
            throw new IllegalStateException("El carrito está vacío");
        }

        // Verificar método de pago
        MetodoPagoModel metodoPago = metodoPagoRepository.findById(metodoPagoId)
                .orElseThrow(() -> {
                    log.error("Método de pago ID {} no encontrado", metodoPagoId);
                    return new EntityNotFoundException("Método de pago no encontrado");
                });

        // Crear pedido
        PedidoModel pedido = new PedidoModel();
        pedido.setUsuario(usuario);
        pedido.setMetodoPago(metodoPago);
        pedido.setEstado(EstadoPedido.PENDIENTE);

        BigDecimal total = BigDecimal.ZERO;

        for (CarritoItemModel item : carrito.getItems()) {

            BigDecimal precioUnitario = BigDecimal.valueOf(item.getProducto().getPrecio());
            BigDecimal subtotal = precioUnitario.multiply(BigDecimal.valueOf(item.getCantidad()));

            // Crear detalle
            DetallePedidoModel detalle = new DetallePedidoModel();
            detalle.setProducto(item.getProducto());
            detalle.setCantidad(item.getCantidad());
            detalle.setPrecioUnitario(precioUnitario);

            pedido.addDetalle(detalle);
            total = total.add(subtotal);

            log.info("Detalle agregado: {} x{} => {}", 
                     item.getProducto().getName(), 
                     item.getCantidad(),
                     subtotal);
        }

        pedido.setTotal(total);

        log.info("Total del pedido: {}", total);

        // Guardar pedido
        PedidoModel pedidoGuardado = pedidoRepository.save(pedido);
        log.info("Pedido guardado con ID {}", pedidoGuardado.getId());

        // Vaciar carrito
        carritoService.vaciarCarrito(usuario);
        log.info("Carrito del usuario ID {} vaciado", usuario.getId());

        return pedidoGuardado;
    }

    public Optional<PedidoModel> obtenerPedidoPorId(Long id) {
        return pedidoRepository.findById(id);
    }
}
