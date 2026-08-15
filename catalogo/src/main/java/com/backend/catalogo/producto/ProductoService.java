package com.backend.catalogo.producto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.catalogo.categoria.CategoriaService;
import com.backend.catalogo.marca.MarcaService;
import com.backend.catalogo.producto.dto.ProductoDtos.AplicarDescuentoRequest;
import com.backend.catalogo.producto.dto.ProductoDtos.LineaPrecio;
import com.backend.catalogo.producto.dto.ProductoDtos.PaginaResponse;
import com.backend.catalogo.producto.dto.ProductoDtos.ProductoRequest;
import com.backend.catalogo.producto.dto.ProductoDtos.ProductoResponse;
import com.backend.catalogo.shared.error.ConflictoException;
import com.backend.catalogo.shared.metricas.MetricasSeguridad;
import com.backend.catalogo.shared.error.RecursoNoEncontradoException;
import com.backend.catalogo.valoracion.ValoracionRepository;
import com.backend.catalogo.valoracion.ValoracionService;

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
    private final ValoracionService valoracionService;
    private final MetricasSeguridad metricas;

    public List<ProductoResponse> listar(String busqueda) {
        List<Producto> productos = (busqueda == null || busqueda.isBlank())
                ? repositorio.listarConRelaciones()
                : repositorio.buscarPorTexto(busqueda.trim());

        return aRespuestas(productos);
    }

    public ProductoResponse obtener(Long id) {
        Producto producto = repositorio.buscarConRelaciones(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto " + id + " no encontrado"));

        ValoracionRepository.ResumenValoracion resumen = resumenDe(List.of(id)).get(id);
        return aRespuesta(producto, Instant.now(), resumen);
    }

    public PaginaResponse<ProductoResponse> listarPorCategoria(String slug, int page, int size) {
        // Valida que la categoría exista para poder responder 404 en vez de una página vacía.
        categoriaService.obtenerPorSlug(slug);

        Page<Producto> pagina = repositorio.listarPorCategoriaSlug(slug, PageRequest.of(page, size));

        return new PaginaResponse<>(
                aRespuestas(pagina.getContent()),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages());
    }

    /** Consumido por `compras` para calcular el total en el servidor. */
    public List<LineaPrecio> precios(List<Long> ids) {
        return repositorio.findByIdIn(ids).stream().map(LineaPrecio::desde).toList();
    }

    private List<ProductoResponse> aRespuestas(List<Producto> productos) {
        Instant ahora = Instant.now();
        Map<Long, ValoracionRepository.ResumenValoracion> resumenes =
                resumenDe(productos.stream().map(Producto::getId).toList());
        return productos.stream()
                .map(p -> aRespuesta(p, ahora, resumenes.get(p.getId())))
                .toList();
    }

    private ProductoResponse aRespuesta(Producto p, Instant ahora,
            ValoracionRepository.ResumenValoracion resumen) {
        return ProductoResponse.desde(p, ahora,
                resumen == null ? null : resumen.getPromedio(),
                resumen == null ? null : resumen.getCantidad());
    }

    private Map<Long, ValoracionRepository.ResumenValoracion> resumenDe(List<Long> productoIds) {
        return valoracionService.resumenPorProductos(productoIds);
    }

    /**
     * Aplica un descuento (porcentaje o monto) a un lote de productos. El
     * precio de oferta se calcula y guarda en el momento; `precio` (el de
     * lista) no cambia, para poder mostrar el "antes" en la tienda.
     */
    @Transactional
    public List<ProductoResponse> aplicarDescuento(AplicarDescuentoRequest dto) {
        if (dto.fin().isBefore(dto.inicio())) {
            throw new ConflictoException("La fecha de fin debe ser posterior a la de inicio");
        }
        if (!"PORCENTAJE".equals(dto.tipo()) && !"MONTO".equals(dto.tipo())) {
            throw new ConflictoException("Tipo de descuento no válido");
        }

        List<Producto> productos = repositorio.findAllById(dto.productoIds());
        if (productos.size() != dto.productoIds().size()) {
            throw new RecursoNoEncontradoException("Algunos productos no existen");
        }

        BigDecimal cien = new BigDecimal("100");
        for (Producto p : productos) {
            BigDecimal oferta = "PORCENTAJE".equals(dto.tipo())
                    ? p.getPrecio()
                            .multiply(BigDecimal.ONE
                                    .subtract(dto.valor().divide(cien, 10, RoundingMode.HALF_UP)))
                            .setScale(2, RoundingMode.HALF_UP)
                    : p.getPrecio().subtract(dto.valor()).setScale(2, RoundingMode.HALF_UP);

            if (oferta.compareTo(BigDecimal.ZERO) < 0) {
                throw new ConflictoException(
                        "El descuento no puede superar el precio de '" + p.getName() + "'");
            }

            p.setPrecioOferta(oferta);
            p.setDescuentoTipo(dto.tipo());
            p.setDescuentoValor(dto.valor());
            p.setOfertaInicio(dto.inicio());
            p.setOfertaFin(dto.fin());
        }

        return productos.stream().map(ProductoResponse::desde).toList();
    }

    /** Quita el descuento del lote indicado; el precio de lista queda vigente. */
    @Transactional
    public List<ProductoResponse> quitarDescuento(List<Long> productoIds) {
        List<Producto> productos = repositorio.findAllById(productoIds);
        for (Producto p : productos) {
            p.setPrecioOferta(null);
            p.setDescuentoTipo(null);
            p.setDescuentoValor(null);
            p.setOfertaInicio(null);
            p.setOfertaFin(null);
        }
        return productos.stream().map(ProductoResponse::desde).toList();
    }

    /**
     * Alta desde el panel: producto de la tienda, sin dueño y ya publicado.
     * Para el alta de un colaborador está {@link #crearComoColaborador}.
     */
    @Transactional
    public ProductoResponse crear(ProductoRequest dto) {
        Producto producto = Producto.builder()
                .name(dto.name())
                .description(dto.description())
                .specifications(dto.specifications())
                .precio(dto.precio())
                .stock(dto.stock())
                .categoria(categoriaService.buscar(dto.categoriaId()))
                .marca(dto.marcaId() != null ? marcaService.buscar(dto.marcaId()) : null)
                .build();
        aplicarImagenes(producto, dto.imagenes());

        return ProductoResponse.desde(repositorio.save(producto));
    }

    @Transactional
    public ProductoResponse actualizar(Long id, ProductoRequest dto) {
        Producto producto = repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto " + id + " no encontrado"));

        producto.setName(dto.name());
        producto.setDescription(dto.description());
        producto.setSpecifications(dto.specifications());
        producto.setPrecio(dto.precio());
        producto.setStock(dto.stock());
        producto.setCategoria(categoriaService.buscar(dto.categoriaId()));
        producto.setMarca(dto.marcaId() != null ? marcaService.buscar(dto.marcaId()) : null);
        aplicarImagenes(producto, dto.imagenes());

        return ProductoResponse.desde(repositorio.save(producto));
    }


    /* ── Productos de colaborador (SZ-B08) ── */

    /**
     * Tope de productos por colaborador.
     *
     * <p>No es una regla de negocio caprichosa: sin tope, una cuenta aprobada
     * puede llenar la cola de moderación de basura y dejar a los demás sin que
     * nadie les revise nada. El número es alto para no estorbar a quien vende de
     * verdad.
     */
    private static final long MAXIMO_POR_COLABORADOR = 200;

    /** Lo que ve el colaborador en su panel: todo lo suyo, aprobado o no. */
    public List<ProductoResponse> mios(Long propietarioId) {
        return aRespuestas(repositorio.listarDelPropietario(propietarioId));
    }

    /** La cola de revisión. Lo más antiguo primero. */
    public List<ProductoResponse> enModeracion(EstadoModeracion estado) {
        return aRespuestas(repositorio.listarPorEstadoModeracion(
                estado == null ? EstadoModeracion.PENDIENTE : estado));
    }

    /**
     * Publica un producto a nombre de un colaborador. Nace PENDIENTE: nadie
     * publica en la tienda sin que lo mire una persona.
     */
    @Transactional
    public ProductoResponse crearComoColaborador(Long propietarioId, ProductoRequest dto) {
        if (repositorio.countByPropietarioId(propietarioId) >= MAXIMO_POR_COLABORADOR) {
            throw new ConflictoException(
                    "Has alcanzado el máximo de %d productos. Elimina alguno para publicar otro."
                            .formatted(MAXIMO_POR_COLABORADOR));
        }

        Producto producto = Producto.builder()
                .name(dto.name())
                .description(dto.description())
                .specifications(dto.specifications())
                .precio(dto.precio())
                .stock(dto.stock())
                .categoria(categoriaService.buscar(dto.categoriaId()))
                .marca(dto.marcaId() != null ? marcaService.buscar(dto.marcaId()) : null)
                .propietarioId(propietarioId)
                .build();
        producto.enviarAModeracion();
        aplicarImagenes(producto, dto.imagenes());

        log.info("Producto de colaborador {} enviado a moderación", propietarioId);
        return ProductoResponse.desde(repositorio.save(producto));
    }

    /**
     * Edita un producto propio. <b>Vuelve a PENDIENTE.</b>
     *
     * <p>Eso es lo que hace útil la moderación: si al editar conservara el visto
     * bueno, bastaría con publicar algo inocuo, esperar la aprobación y cambiarlo
     * después por otra cosa.
     */
    @Transactional
    public ProductoResponse actualizarComoColaborador(Long id, Long propietarioId, ProductoRequest dto) {
        Producto producto = exigirPropio(id, propietarioId);

        producto.setName(dto.name());
        producto.setDescription(dto.description());
        producto.setSpecifications(dto.specifications());
        producto.setPrecio(dto.precio());
        producto.setStock(dto.stock());
        producto.setCategoria(categoriaService.buscar(dto.categoriaId()));
        producto.setMarca(dto.marcaId() != null ? marcaService.buscar(dto.marcaId()) : null);
        aplicarImagenes(producto, dto.imagenes());
        producto.enviarAModeracion();

        return ProductoResponse.desde(repositorio.save(producto));
    }

    @Transactional
    public void eliminarComoColaborador(Long id, Long propietarioId) {
        repositorio.delete(exigirPropio(id, propietarioId));
    }

    @Transactional
    public ProductoResponse aprobarModeracion(Long id, Long moderadorId) {
        Producto producto = buscarParaModerar(id);
        producto.aprobarModeracion(moderadorId);
        log.info("Producto {} aprobado por {}", id, moderadorId);
        metricas.moderacionProducto("aprobado");
        return ProductoResponse.desde(repositorio.save(producto));
    }

    @Transactional
    public ProductoResponse rechazarModeracion(Long id, Long moderadorId, String motivo) {
        Producto producto = buscarParaModerar(id);
        producto.rechazarModeracion(moderadorId, motivo);
        log.info("Producto {} rechazado por {}", id, moderadorId);
        // Una tasa de rechazo alta avisa de dos cosas posibles: colaboradores
        // que no entienden qué se pide, o alguien colando basura.
        metricas.moderacionProducto("rechazado");
        return ProductoResponse.desde(repositorio.save(producto));
    }

    /**
     * El producto tiene que existir Y ser suyo.
     *
     * <p>Se responde 404 y no 403 cuando es de otro: un 403 confirmaría que ese
     * id existe, y probando números se sabría qué publica la competencia antes de
     * que se apruebe.
     */
    private Producto exigirPropio(Long id, Long propietarioId) {
        Producto producto = repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto " + id + " no encontrado"));

        if (!producto.perteneceA(propietarioId)) {
            throw new RecursoNoEncontradoException("Producto " + id + " no encontrado");
        }
        return producto;
    }

    private Producto buscarParaModerar(Long id) {
        Producto producto = repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto " + id + " no encontrado"));

        if (producto.esDeLaTienda()) {
            throw new ConflictoException("Los productos de la tienda no pasan por moderación");
        }
        return producto;
    }

    /**
     * Reemplaza la galería del producto. La primera imagen queda también en
     * `imageUrl` (imagen principal) para no romper tarjetas ni el carrito, que
     * consumen una única imagen.
     *
     * Las filas existentes se REUTILIZAN en vez de borrarlas y volver a
     * insertarlas: dentro de un mismo flush Hibernate emite los INSERT antes que
     * los DELETE, así que un `clear()` + `add()` chocaba contra el índice único
     * `uk_producto_imagen_producto_posicion` y la edición moría con un 409.
     */
    private void aplicarImagenes(Producto producto, List<String> urls) {
        List<String> limpias = urls == null ? List.of()
                : urls.stream().map(String::trim).filter(u -> !u.isBlank()).toList();

        List<ProductoImagen> actuales = producto.getImagenes();

        for (int i = 0; i < limpias.size(); i++) {
            if (i < actuales.size()) {
                ProductoImagen existente = actuales.get(i);
                existente.setUrl(limpias.get(i));
                existente.setPosicion(i);
            } else {
                actuales.add(ProductoImagen.builder()
                        .producto(producto)
                        .url(limpias.get(i))
                        .posicion(i)
                        .build());
            }
        }

        // Las que sobran desaparecen por orphanRemoval.
        if (actuales.size() > limpias.size()) {
            actuales.subList(limpias.size(), actuales.size()).clear();
        }

        producto.setImageUrl(limpias.isEmpty() ? null : limpias.get(0));
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
