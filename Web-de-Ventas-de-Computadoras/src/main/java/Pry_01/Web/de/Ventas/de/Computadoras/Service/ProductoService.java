package Pry_01.Web.de.Ventas.de.Computadoras.Service;

import java.util.List;

import org.springframework.stereotype.Service;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.ProductoModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.ProductoRepository;

@Service
public class ProductoService {
    private final ProductoRepository productoRepository;

    public ProductoService(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    public List<ProductoModel> listarProducto() {
        return productoRepository.findAll();
    }

    public ProductoModel guardarProducto(ProductoModel producto) {
        return productoRepository.save(producto);
    }

    public ProductoModel obtenerPorId(Long id) {
        return productoRepository.findById(id).orElse(null);
    }

    public void eliminarProducto(Long id) {
        productoRepository.deleteById(id);
    }

    public void actualizarProducto(ProductoModel producto) {
        productoRepository.save(producto);
    }
}
