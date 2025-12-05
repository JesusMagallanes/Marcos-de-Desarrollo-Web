package Pry_01.Web.de.Ventas.de.Computadoras.Service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.ProductosDTO.ProductosCreateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.ProductosDTO.ProductosResponseDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.ProductosDTO.ProductosUpdateDTO;
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

    // Listar todos los productos
    public List<ProductosResponseDTO> listarProducto() {
        return productoRepository.findAll().stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    // Listar productos por lista de marcas (paginado)
    public Page<ProductosResponseDTO> listarPorMarcas(List<Long> marcaIds, Pageable pageable) {
        List<MarcaModel> marcas = marcaRepository.findAllById(marcaIds);
        if (marcas.isEmpty())
            return Page.empty(pageable);

        Page<ProductoModel> productosPage = productoRepository.findDistinctByMarcasIn(marcas, pageable);
        return productosPage.map(this::convertToResponseDTO);
    }

    // Guardar producto con varias marcas
    public ProductoModel guardarProducto(ProductosCreateDTO dto) {
        List<MarcaModel> marcas = marcaRepository.findAllById(dto.getMarcaIds());
        if (marcas.isEmpty())
            throw new RuntimeException("No se encontraron marcas para los IDs proporcionados");

        ProductoModel producto = new ProductoModel();
        producto.setName(dto.getName());
        producto.setDescription(dto.getDescription());
        producto.setPrecio(dto.getPrecio());
        producto.setImageUrl(dto.getImageUrl());
        producto.setStock(dto.getStock());
        producto.setMarcas(marcas);

        return productoRepository.save(producto);
    }

    // Actualizar producto con varias marcas
    public ProductoModel actualizarProducto(Long id, ProductosUpdateDTO dto) {
        ProductoModel producto = productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        List<MarcaModel> marcas = marcaRepository.findAllById(dto.getMarcaIds());
        if (marcas.isEmpty())
            throw new RuntimeException("No se encontraron marcas para los IDs proporcionados");

        producto.setName(dto.getName());
        producto.setDescription(dto.getDescription());
        producto.setPrecio(dto.getPrecio());
        producto.setImageUrl(dto.getImageUrl());
        producto.setStock(dto.getStock());
        producto.setMarcas(marcas);

        return productoRepository.save(producto);
    }

    // Eliminar producto
    public void eliminarProducto(Long id) {
        productoRepository.deleteById(id);
    }

    // Obtener producto por ID
    public ProductosResponseDTO obtenerProductoPorId(Long id) {
        ProductoModel producto = productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
        return convertToResponseDTO(producto);
    }

    public ProductoModel obtenerPorId(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
    }

    // Convertir ProductoModel a DTO
    private ProductosResponseDTO convertToResponseDTO(ProductoModel producto) {

        String marcasNombres = producto.getMarcas().stream()
                .map(MarcaModel::getNombre)
                .collect(Collectors.joining(", "));

        String categoriasNombres = producto.getMarcas().stream()
                .map(m -> m.getCategoria().getName())
                .distinct()
                .collect(Collectors.joining(", "));

        List<Long> marcaIds = producto.getMarcas().stream()
                .map(MarcaModel::getId)
                .collect(Collectors.toList());

        List<Long> categoriaIds = producto.getMarcas().stream()
                .map(m -> m.getCategoria().getId())
                .distinct()
                .collect(Collectors.toList());

        return new ProductosResponseDTO(
                producto.getId(),
                producto.getName(),
                producto.getDescription(),
                producto.getPrecio(),
                producto.getImageUrl(),
                producto.getStock(),
                marcasNombres,
                categoriasNombres,
                marcaIds,
                categoriaIds);
    }

}
