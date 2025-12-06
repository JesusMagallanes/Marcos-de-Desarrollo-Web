package Pry_01.Web.de.Ventas.de.Computadoras.Service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.ProductosDTO.ProductosCreateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.ProductosDTO.ProductosResponseDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.ProductosDTO.ProductosUpdateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.CategoriaModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.MarcaModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.ProductoModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.CategoriaRepository;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.MarcaRepository;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.ProductoRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final MarcaRepository marcaRepository;

    public List<ProductosResponseDTO> listarProducto() {
        List<ProductoModel> productos = productoRepository.findAll();
        return productos.stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    public Page<ProductosResponseDTO> listarPorCategoria(CategoriaModel categoria, Pageable pageable) {
        Page<ProductoModel> productosPage = productoRepository.findByCategoriaId(categoria, pageable);
        return productosPage.map(this::convertToResponseDTO);
    }

    public ProductoModel guardarProducto(ProductosCreateDTO dto) {
        CategoriaModel categoria = categoriaRepository.findById(dto.getCategoriaId())
                .orElseThrow(() -> new RuntimeException("Categoria no encontrada"));

        MarcaModel marca = marcaRepository.findById(dto.getMarcaId())
                .orElseThrow(() -> new RuntimeException("Marca no encontrada"));

        ProductoModel producto = new ProductoModel();
        producto.setName(dto.getName());
        producto.setDescription(dto.getDescription());
        producto.setPrecio(dto.getPrecio());
        producto.setImageUrl(dto.getImageUrl());
        producto.setStock(dto.getStock());
        producto.setCategoriaId(categoria);
        producto.setMarca(marca);

        return productoRepository.save(producto);
    }

    public void eliminarProducto(Long id) {
        productoRepository.deleteById(id);
    }

    public ProductoModel actualizarProducto(Long id, ProductosUpdateDTO dto) {
        ProductoModel producto = productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        CategoriaModel categoria = categoriaRepository.findById(dto.getCategoriaId())
                .orElseThrow(() -> new RuntimeException("Categoria no encontrada"));

        MarcaModel marca = marcaRepository.findById(dto.getMarcaId())
                .orElseThrow(() -> new RuntimeException("Marca no encontrada"));

        producto.setName(dto.getName());
        producto.setDescription(dto.getDescription());
        producto.setPrecio(dto.getPrecio());
        producto.setImageUrl(dto.getImageUrl());
        producto.setStock(dto.getStock());
        producto.setCategoriaId(categoria);
        producto.setMarca(marca);

        return productoRepository.save(producto);
    }

    public ProductosResponseDTO obtenerProductoPorId(Long id) {
        ProductoModel producto = productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
        return convertToResponseDTO(producto);
    }

    private ProductosResponseDTO convertToResponseDTO(ProductoModel producto) {
        return new ProductosResponseDTO(
                producto.getId(),
                producto.getName(),
                producto.getDescription(),
                producto.getPrecio(),
                producto.getImageUrl(),
                producto.getStock(),
                producto.getCategoriaId().getName(),
                producto.getMarca() != null ? producto.getMarca().getName() : null,
                producto.getCategoriaId().getId(),
                producto.getMarca() != null ? producto.getMarca().getId() : null
        );
    }

    public ProductoModel obtenerPorId(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
    }
}
