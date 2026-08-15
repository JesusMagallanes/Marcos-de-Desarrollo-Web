package com.backend.compras.pedido;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.compras.carrito.Carrito;
import com.backend.compras.carrito.CarritoRepository;
import com.backend.compras.carrito.CarritoService;
import com.backend.compras.carrito.dto.CarritoDtos.ItemResponse;
import com.backend.compras.metodopago.MetodoPago;
import com.backend.compras.metodopago.MetodoPagoRepository;
import com.backend.compras.pedido.dto.PedidoDtos.PedidoResponse;
import com.backend.compras.shared.error.ConflictoException;
import com.backend.compras.shared.error.RecursoNoEncontradoException;
import com.backend.compras.shared.security.UsuarioAutenticado;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PedidoService {

    private final PedidoRepository pedidoRepositorio;
    private final CarritoRepository carritoRepositorio;
    private final CarritoService carritoService;
    private final MetodoPagoRepository metodoPagoRepositorio;

    public List<PedidoResponse> misPedidos(Long usuarioId) {
        return pedidoRepositorio.listarPorUsuario(usuarioId).stream().map(PedidoResponse::desde).toList();
    }

    public List<PedidoResponse> listar(EstadoPedido estado) {
        List<Pedido> pedidos = estado == null
                ? pedidoRepositorio.listarTodos()
                : pedidoRepositorio.listarPorEstado(estado);
        return pedidos.stream().map(PedidoResponse::desde).toList();
    }

    /** Un cliente solo ve sus pedidos; el staff ve cualquiera. */
    public PedidoResponse obtener(Long id, UsuarioAutenticado usuario) {
        Pedido pedido = pedidoRepositorio.buscarConDetalles(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido " + id + " no encontrado"));

        // A01: se responde 404 y no 403 para no confirmar que el pedido existe.
        if (!usuario.esStaff() && !pedido.getUsuarioId().equals(usuario.id())) {
            throw new RecursoNoEncontradoException("Pedido " + id + " no encontrado");
        }
        return PedidoResponse.desde(pedido);
    }

    /** Crea el pedido en estado PENDIENTE como paso 2 de la saga de compra. */
    @Transactional
    public Pedido crearDesdeCarrito(Long usuarioId, Long metodoPagoId, String referencia, BigDecimal total) {

        Carrito carrito = carritoRepositorio.buscarConItems(usuarioId)
                .orElseThrow(() -> new ConflictoException("Tu carrito está vacío"));

        if (carrito.getItems().isEmpty()) {
            throw new ConflictoException("Tu carrito está vacío");
        }

        MetodoPago metodoPago = metodoPagoRepositorio.findById(metodoPagoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Método de pago no encontrado"));

        Pedido pedido = Pedido.builder()
                .usuarioId(usuarioId)
                .metodoPago(metodoPago)
                .estado(EstadoPedido.PENDIENTE)
                .referenciaSaga(referencia)
                .total(total)
                .build();

        // Los precios ya vienen resueltos por la saga; se copian a la línea de
        // pedido para que el histórico no dependa de catálogo.
        for (ItemResponse item : carritoService.construir(carrito).items()) {
            DetallePedido detalle = DetallePedido.builder()
                    .productoId(item.productId())
                    .productoNombre(item.nombre())
                    .productoImagen(item.image())
                    .cantidad(item.cantidad())
                    .precioUnitario(item.precio())
                    .build();
            detalle.calcularTotal();
            pedido.agregarDetalle(detalle);
        }

        Pedido guardado = pedidoRepositorio.save(pedido);
        log.info("Pedido {} creado (PENDIENTE) para el usuario {} por {}",
                guardado.getId(), usuarioId, total);
        return guardado;
    }

    /** Cambio de estado manual desde el panel de envíos. */
    @Transactional
    public PedidoResponse cambiarEstado(Long id, EstadoPedido nuevoEstado) {
        Pedido pedido = pedidoRepositorio.buscarConDetalles(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido " + id + " no encontrado"));

        pedido.cambiarEstado(nuevoEstado);
        return PedidoResponse.desde(pedidoRepositorio.save(pedido));
    }

    @Transactional
    public void eliminar(Long id) {
        if (!pedidoRepositorio.existsById(id)) {
            throw new RecursoNoEncontradoException("Pedido " + id + " no encontrado");
        }
        pedidoRepositorio.deleteById(id);
    }

    /** ¿Este usuario compró este producto? Ver PedidoRepository.comproElProducto. */
    public boolean comproElProducto(Long usuarioId, Long productoId) {
        return pedidoRepositorio.comproElProducto(usuarioId, productoId);
    }
}
