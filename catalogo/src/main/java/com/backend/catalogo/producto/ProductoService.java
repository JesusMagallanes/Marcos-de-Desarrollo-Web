package com.backend.catalogo.producto;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.catalogo.categoria.CategoriaService;
import com.backend.catalogo.marca.MarcaService;
import com.backend.catalogo.producto.dto.ProductoDtos.LineaPrecio;
import com.backend.catalogo.producto.dto.ProductoDtos.PaginaResponse;
import com.backend.catalogo.producto.dto.ProductoDtos.ProductoRequest;
import com.backend.catalogo.producto.dto.ProductoDtos.ProductoResponse;
import com.backend.catalogo.shared.error.RecursoNoEncontradoException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ProductoService {

    private final ProductoRepository repositorio;
    private final CategoriaService categoriaService;
    private final MarcaService marcaService;

    public List<ProductoResponse> listar(String busqueda) {
        List<Producto> productos = (busqueda == null || busqueda.isBlank())
                ? repositorio.listarConRelaciones()
                : repositorio.buscarPorTexto(busqueda.trim());

        return productos.stream().map(ProductoResponse::desde).toList();
    }

    public ProductoResponse obtener(Long id) {
        return repositorio.buscarConRelaciones(id)
                .map(ProductoResponse::desde)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto " + id + " no encontrado"));
    }

    public PaginaResponse<ProductoResponse> listarPorCategoria(String slug, int page, int size) {
        // Valida que la categoría exista para poder responder 404 en vez de una página vacía.
        categoriaService.obtenerPorSlug(slug);

        Page<Producto> pagina = repositorio.listarPorCategoriaSlug(slug, PageRequest.of(page, size));

        return new PaginaResponse<>(
                pagina.getContent().stream().map(ProductoResponse::desde).toList(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages());
    }

    /** Consumido por `compras` para calcular el total en el servidor. */
    public List<LineaPrecio> precios(List<Long> ids) {
        return repositorio.findByIdIn(ids).stream().map(LineaPrecio::desde).toList();
    }

    @Transactional
    public ProductoResponse crear(ProductoRequest dto) {
        Producto producto = Producto.builder()
                .name(dto.name())
                .description(dto.description())
                .precio(dto.precio())
                .imageUrl(dto.imageUrl())
                .stock(dto.stock())
                .categoria(categoriaService.buscar(dto.categoriaId()))
                .marca(dto.marcaId() != null ? marcaService.buscar(dto.marcaId()) : null)
                .build();

        return ProductoResponse.desde(repositorio.save(producto));
    }

    @Transactional
    public ProductoResponse actualizar(Long id, ProductoRequest dto) {
        Producto producto = repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto " + id + " no encontrado"));

        producto.setName(dto.name());
        producto.setDescription(dto.description());
        producto.setPrecio(dto.precio());
        producto.setImageUrl(dto.imageUrl());
        producto.setStock(dto.stock());
        producto.setCategoria(categoriaService.buscar(dto.categoriaId()));
        producto.setMarca(dto.marcaId() != null ? marcaService.buscar(dto.marcaId()) : null);

        return ProductoResponse.desde(repositorio.save(producto));
    }

    @Transactional
    public void eliminar(Long id) {
        if (!repositorio.existsById(id)) {
            throw new RecursoNoEncontradoException("Producto " + id + " no encontrado");
        }
        repositorio.deleteById(id);
    }

    /*
     * El ajuste de stock vive ahora en InventarioService, que lo gestiona
     * mediante reservas con caducidad: descontar directamente no permitía
     * compensar si el pago fallaba a mitad de la saga.
     */
}
