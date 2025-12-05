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

    public List<ProductosResponseDTO> listarProducto() {
        return productoRepository.findAll()
                .stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    public Page<ProductosResponseDTO> listarPorMarca(MarcaModel marca, Pageable pageable) {
        return productoRepository.findByMarcas(marca, pageable)
                .map(this::convertToResponseDTO);
    }

    public void eliminarProducto(Long id) {
        ProductoModel producto = obtenerPorId(id);
        productoRepository.delete(producto);
    }
    public ProductoModel guardarProducto(ProductosCreateDTO dto) {

        List<MarcaModel> marcas = marcaRepository.findAllById(dto.getMarcaIds());
        if (marcas.isEmpty())
            throw new RuntimeException("No se encontraron marcas");

        ProductoModel producto = new ProductoModel();
        producto.setName(dto.getName());
        producto.setDescription(dto.getDescription());
        producto.setPrecio(dto.getPrecio());
        producto.setImageUrl(dto.getImageUrl());
        producto.setStock(dto.getStock());
        producto.setMarcas(marcas);

        return productoRepository.save(producto);
    }

    public ProductoModel actualizarProducto(Long id, ProductosUpdateDTO dto) {

        ProductoModel producto = obtenerPorId(id);

        List<MarcaModel> marcas = marcaRepository.findAllById(dto.getMarcaIds());
        if (marcas.isEmpty())
            throw new RuntimeException("No se encontraron marcas");

        producto.setName(dto.getName());
        producto.setDescription(dto.getDescription());
        producto.setPrecio(dto.getPrecio());
        producto.setImageUrl(dto.getImageUrl());
        producto.setStock(dto.getStock());
        producto.setMarcas(marcas);

        return productoRepository.save(producto);
    }

    public ProductoModel obtenerPorId(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
    }

    public ProductosResponseDTO obtenerProductoDTO(Long id) {
        return convertToResponseDTO(obtenerPorId(id));
    }

    private ProductosResponseDTO convertToResponseDTO(ProductoModel producto) {

        List<String> marcas = producto.getMarcas()
                .stream()
                .map(MarcaModel::getNombre)
                .collect(Collectors.toList());

        List<String> categorias = producto.getMarcas()
                .stream()
                .map(m -> m.getCategoria().getName())
                .collect(Collectors.toList());

        return new ProductosResponseDTO(
                producto.getId(),
                producto.getName(),
                producto.getDescription(),
                producto.getPrecio(),
                producto.getImageUrl(),
                producto.getStock(),
                marcas,
                categorias
        );
    }
}
