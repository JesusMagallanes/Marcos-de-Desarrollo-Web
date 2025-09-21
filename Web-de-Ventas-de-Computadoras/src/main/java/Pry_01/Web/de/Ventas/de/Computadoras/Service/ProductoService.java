package Pry_01.Web.de.Ventas.de.Computadoras.Service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import Pry_01.Web.de.Ventas.de.Computadoras.Dto.ProductoDto;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.CategoriaModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.ProductoModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.CategoriaRepository;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.ProductoRepository;
import jakarta.persistence.EntityNotFoundException;

@Service
public class ProductoService {
    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    public ProductoService(ProductoRepository productoRepository, CategoriaRepository categoriaRepository) {
        this.categoriaRepository=categoriaRepository;
        this.productoRepository = productoRepository;
    }

    public List<ProductoModel> listarProductos() {
        return productoRepository.findAll();
    }

    public void eliminarProductoPorId(Long id) {
        if (productoRepository.existsById(id)) {
            productoRepository.deleteById(id);
        } else {
            throw new EntityNotFoundException("Producto con ID " + id + " no existe");
        }
    }

    public ProductoModel guardarProducto(ProductoModel producto) {
        if (productoRepository.existsByName(producto.getName())) {
            throw new IllegalArgumentException("La producto ya existe");
        } else {
            return productoRepository.save(producto);
        }
    }

    public Optional<ProductoModel> actualizarProducto(Long id, ProductoDto productoDto) {
        Optional<ProductoModel> productoOptional = productoRepository.findById(id);
        if (!productoOptional.isPresent()) {
            return Optional.empty();
        }
        ProductoModel producto = productoOptional.get();

        if (productoDto.getName() != null) {
            producto.setName(productoDto.getName());
            ;
        }
        if (productoDto.getDescription() != null) {
            producto.setDescription(productoDto.getDescription());
        }
        if (productoDto.getPrecio() > 0) {
            producto.setPrecio(productoDto.getPrecio());
        }
        if (productoDto.getImageUrl() != null) {
            producto.setImageUrl(productoDto.getImageUrl());
        }
        if (productoDto.getStock() > 0) {
            producto.setStock(productoDto.getStock());
        }
        if (productoDto.getCategoriaId() != null) {
            CategoriaModel categoria = categoriaRepository.findById(productoDto.getCategoriaId()).orElse(null);
            if (categoria != null) {
                producto.setCategoria(categoria);
            }
        }

        ProductoModel ProductoActualizado = productoRepository.save(producto);

        return Optional.of(ProductoActualizado);
    }

}
