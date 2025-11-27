package Pry_01.Web.de.Ventas.de.Computadoras.Service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.CarritoItemModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.CarritoModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.ProductoModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.CarritoItemRepository;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.CarritoRepository;
import jakarta.transaction.Transactional;

@Service
@Transactional
public class CarritoService {

    private final CarritoRepository carritoRepo;
    private final CarritoItemRepository itemRepo;
    private final ProductoService productoService;

    public CarritoService(CarritoRepository carritoRepo, CarritoItemRepository itemRepo, ProductoService productoService) {
        this.carritoRepo = carritoRepo;
        this.itemRepo = itemRepo;
        this.productoService = productoService;
    }

    public CarritoModel obtenerCarrito(UsuarioModel usuario) {
        return carritoRepo.findByUsuario(usuario)
                .orElseGet(() -> {
                    CarritoModel nuevo = new CarritoModel();
                    nuevo.setUsuario(usuario);
                    return carritoRepo.save(nuevo);
                });
    }

    public void agregarProducto(UsuarioModel usuario, Long productoId) {
        CarritoModel carrito = obtenerCarrito(usuario);
        ProductoModel producto = productoService.obtenerPorId(productoId);

        // Verificar si el producto ya está en el carrito
        Optional<CarritoItemModel> existente = itemRepo.findByCarritoAndProducto(carrito, producto);

        if (existente.isPresent()) {
            CarritoItemModel item = existente.get();
            item.setCantidad(item.getCantidad() + 1);
            itemRepo.save(item);
        } else {
            CarritoItemModel nuevo = new CarritoItemModel();
            nuevo.setCarrito(carrito);
            nuevo.setProducto(producto);
            nuevo.setCantidad(1);
            itemRepo.save(nuevo);
        }
    }

    public void agregarProductoConCantidad(UsuarioModel usuario, Long productoId, int cantidad) {
        if (cantidad <= 0) return;
        CarritoModel carrito = obtenerCarrito(usuario);
        ProductoModel producto = productoService.obtenerPorId(productoId);

        Optional<CarritoItemModel> existente = itemRepo.findByCarritoAndProducto(carrito, producto);
        if (existente.isPresent()) {
            CarritoItemModel item = existente.get();
            item.setCantidad(item.getCantidad() + cantidad);
            itemRepo.save(item);
        } else {
            CarritoItemModel nuevo = new CarritoItemModel();
            nuevo.setCarrito(carrito);
            nuevo.setProducto(producto);
            nuevo.setCantidad(cantidad);
            itemRepo.save(nuevo);
        }
    }

    public void eliminarItem(Long itemId) {
        itemRepo.deleteById(itemId);
    }

    public void vaciarCarrito(UsuarioModel usuario) {
        CarritoModel carrito = obtenerCarrito(usuario);
        carrito.getItems().clear();
        carritoRepo.save(carrito);
    }

    public double calcularTotal(UsuarioModel usuario) {
        CarritoModel carrito = obtenerCarrito(usuario);
        return carrito.getItems().stream()
                .mapToDouble(i -> i.getProducto().getPrecio() * i.getCantidad())
                .sum();
    }
}
