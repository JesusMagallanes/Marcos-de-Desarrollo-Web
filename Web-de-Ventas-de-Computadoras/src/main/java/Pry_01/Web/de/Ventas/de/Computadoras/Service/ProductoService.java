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
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.MarcaRepository;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.ProductoRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductoService {
    private final MarcaRepository marcaRepository;
    private final ProductoRepository productoRepository;


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
    
    public Page<ProductosResponseDTO> listarPorMarca(MarcaModel marca, Pageable pageable) {
        Page<ProductoModel> productosPage = productoRepository.findByMarcaId(marca, pageable);

        return productosPage.map(this::convertToResponseDTO);
    }

    public ProductoModel guardarProducto(ProductosCreateDTO dto) {
        MarcaModel marca = marcaRepository.findById(dto.getMarcaId())
                .orElseThrow(() -> new RuntimeException("Marca no encontrada"));
        ProductoModel producto = new ProductoModel();
        producto.setName(dto.getName());
        producto.setDescription(dto.getDescription());
        producto.setPrecio(dto.getPrecio());
        producto.setImageUrl(dto.getImageUrl());
        producto.setStock(dto.getStock());
        producto.setMarcaId(marca);
        return productoRepository.save(producto);
    }

    public void eliminarProducto(Long id) {
        productoRepository.deleteById(id);
    }

    public ProductoModel actualizarProducto(Long id, ProductosUpdateDTO dto) {
        ProductoModel producto = productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
        MarcaModel marca = marcaRepository.findById(dto.getMarcaId())
                .orElseThrow(() -> new RuntimeException("Marca no encontrada"));
        producto.setName(dto.getName());
        producto.setDescription(dto.getDescription());
        producto.setPrecio(dto.getPrecio());
        producto.setImageUrl(dto.getImageUrl());
        producto.setStock(dto.getStock());
        producto.setMarcaId(marca);
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
                producto.getMarcaId().getName(),
                producto.getMarcaId().getCategoriaId().getName()
                );
    }

    public ProductoModel obtenerPorId(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
    }

}
