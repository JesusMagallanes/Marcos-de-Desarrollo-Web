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

@Service
@Transactional
public class PedidoService {
    private final PedidoRepository pedidoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MetodoPagoRepository metodoPagoRepository;
    private final CarritoService carritoService;

    public PedidoService(PedidoRepository pedidoRepository, UsuarioRepository usuarioRepository,
            MetodoPagoRepository metodoPagoRepository, CarritoService carritoService) {
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
        if (pedidoRepository.existsById(id)) {
            pedidoRepository.deleteById(id);
        } else {
            throw new EntityNotFoundException("Pedido con ID " + id + " no existe");
        }
    }

    public PedidoModel guardarPedido(PedidoModel pedido) {
        return pedidoRepository.save(pedido);
    }

    /**
     * Crea un pedido desde el carrito actual del usuario
     * @param usuario Usuario que realiza el pedido
     * @param metodoPagoId ID del método de pago seleccionado
     * @return PedidoModel creado
     */
    public PedidoModel crearPedidoDesdeCarrito(UsuarioModel usuario, Long metodoPagoId) {
        System.out.println("=== Iniciando crearPedidoDesdeCarrito ===");
        System.out.println("Usuario: " + usuario.getName() + " (ID: " + usuario.getId() + ")");
        
        // Obtener el carrito del usuario
        CarritoModel carrito = carritoService.obtenerCarrito(usuario);
        System.out.println("Carrito obtenido. Items: " + carrito.getItems().size());
        
        if (carrito.getItems().isEmpty()) {
            System.out.println("ERROR: El carrito está vacío");
            throw new IllegalStateException("El carrito está vacío");
        }

        // Obtener el método de pago
        MetodoPagoModel metodoPago = metodoPagoRepository.findById(metodoPagoId)
                .orElseThrow(() -> {
                    System.out.println("ERROR: Método de pago no encontrado con ID: " + metodoPagoId);
                    return new EntityNotFoundException("Método de pago no encontrado");
                });
        System.out.println("Método de pago encontrado: " + metodoPago.getName());

        // Crear el pedido
        PedidoModel pedido = new PedidoModel();
        pedido.setUsuario(usuario);
        pedido.setMetodoPago(metodoPago);
        pedido.setEstado(EstadoPedido.PENDIENTE);

        // Calcular total y agregar detalles
        BigDecimal total = BigDecimal.ZERO;
        
        for (CarritoItemModel item : carrito.getItems()) {
            DetallePedidoModel detalle = new DetallePedidoModel();
            detalle.setProducto(item.getProducto());
            detalle.setCantidad(item.getCantidad());
            detalle.setPrecioUnitario(BigDecimal.valueOf(item.getProducto().getPrecio()));
            
            pedido.addDetalle(detalle);
            
            total = total.add(BigDecimal.valueOf(item.getProducto().getPrecio() * item.getCantidad()));
            System.out.println("Detalle agregado: " + item.getProducto().getName() + " x" + item.getCantidad());
        }

        pedido.setTotal(total);
        System.out.println("Total del pedido: " + total);

        // Guardar el pedido
        PedidoModel pedidoGuardado = pedidoRepository.save(pedido);
        System.out.println("Pedido guardado con ID: " + pedidoGuardado.getId());

        // Vaciar el carrito después de crear el pedido
        carritoService.vaciarCarrito(usuario);
        System.out.println("Carrito vaciado");
        System.out.println("=== Fin crearPedidoDesdeCarrito ===");

        return pedidoGuardado;
    }

    public Optional<PedidoModel> obtenerPedidoPorId(Long id) {
        return pedidoRepository.findById(id);
    }
}