package com.backend.catalogo.producto;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.backend.catalogo.producto.dto.ProductoDtos.AplicarDescuentoRequest;
import com.backend.catalogo.producto.dto.ProductoDtos.PaginaDescuentos;
import com.backend.catalogo.producto.dto.ProductoDtos.PaginaResponse;
import com.backend.catalogo.producto.dto.ProductoDtos.PortadaResponse;
import com.backend.catalogo.producto.dto.ProductoDtos.ProductoRequest;
import com.backend.catalogo.producto.dto.ProductoDtos.ProductoResponse;
import com.backend.catalogo.producto.dto.ProductoDtos.QuitarDescuentoRequest;
import com.backend.catalogo.shared.validacion.Limites;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * `@Validated` a nivel de clase activa la validación de @PathVariable y se valida el
 * cuerpo.
 *
 * @RequestParam: sin ella las anotaciones de los parámetros se ignoran y solo
 */
@RestController
@RequestMapping("/api/productos")
@Validated
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService servicio;

    /** GET /api/productos — público. */
    /**
     * La vitrina, paginada.
     *
     * <p>Devolvía una lista con el catálogo completo: sin tope, sin páginas y en
     * el endpoint más visitado de la tienda. `size` va acotado por el mismo
     * `MAX_PAGINA` que el listado por categoría, así que nadie puede pedir el
     * catálogo entero disfrazándolo de una página muy grande.
     */
    @GetMapping
    public PaginaResponse<ProductoResponse> listar(
            @RequestParam(required = false)
            @Size(max = Limites.MAX_BUSQUEDA, message = "La búsqueda es demasiado larga")
            @Pattern(regexp = Limites.TEXTO_BUSQUEDA, message = "La búsqueda tiene caracteres no permitidos")
            String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(Limites.MAX_PAGINA) int size) {
        return servicio.listar(search, page, size);
    }

    /**
     * Todo lo que enseña la portada, en una respuesta.
     *
     * <p>Existe porque la portada se descargaba el catálogo entero para quedarse
     * con unas decenas de productos: diez destacados, los que estuvieran en
     * oferta y doce por categoría. Las tres listas son acotadas y el servidor
     * puede resolverlas; hacerlo en el navegador obligaba a traérselo todo.
     */
    @GetMapping("/portada")
    public PortadaResponse portada() {
        return servicio.portada();
    }

    /**
     * El listado del panel de descuentos: página y conteos de las pestañas.
     *
     * <p>Endpoint aparte y no un parámetro más del listado público porque lo que
     * pide es distinto: filtra por estado del descuento, admite categoría y
     * marca, y devuelve además los conteos por sección. Meter todo eso en la
     * vitrina pública cargaría el endpoint más visitado con la forma que
     * necesita una pantalla de administración.
     *
     * <p>`estado` es una lista cerrada: decide una rama del WHERE, así que no
     * puede ser texto libre que llegue hasta la consulta a ver qué pasa.
     */
    @GetMapping("/descuentos")
    @PreAuthorize("hasAuthority('PERMISO_PRODUCTOS_GESTIONAR')")
    public PaginaDescuentos paraDescuentos(
            @RequestParam(defaultValue = "todos")
            @Pattern(regexp = "todos|activo|programado|inactivo",
                    message = "Estado de descuento no válido") String estado,
            @RequestParam(required = false) @Positive Long categoriaId,
            @RequestParam(required = false) @Positive Long marcaId,
            @RequestParam(required = false)
            @Size(max = Limites.MAX_BUSQUEDA, message = "La búsqueda es demasiado larga")
            @Pattern(regexp = Limites.TEXTO_BUSQUEDA, message = "La búsqueda tiene caracteres no permitidos")
            String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(Limites.MAX_PAGINA) int size) {

        return servicio.paraDescuentos(estado, categoriaId, marcaId, search, page, size);
    }

    @GetMapping("/{id}")
    public ProductoResponse obtener(@PathVariable @Positive Long id) {
        return servicio.obtener(id);
    }

    /** GET /api/productos/categoria/{slug} — público y paginado. */
    @GetMapping("/categoria/{slug}")
    public PaginaResponse<ProductoResponse> porCategoria(
            @PathVariable @Pattern(regexp = Limites.SLUG, message = "Slug no válido") String slug,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(Limites.MAX_PAGINA) int size) {
        return servicio.listarPorCategoria(slug, page, size);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERMISO_PRODUCTOS_GESTIONAR')")
    public ResponseEntity<ProductoResponse> crear(@Valid @RequestBody ProductoRequest dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISO_PRODUCTOS_GESTIONAR')")
    public ProductoResponse actualizar(@PathVariable @Positive Long id,
            @Valid @RequestBody ProductoRequest dto) {
        return servicio.actualizar(id, dto);
    }

    /** POST /api/productos/descuento — aplica un descuento a un lote de productos. */
    @PostMapping("/descuento")
    @PreAuthorize("hasAuthority('PERMISO_PRODUCTOS_GESTIONAR')")
    public List<ProductoResponse> aplicarDescuento(
            @Valid @RequestBody AplicarDescuentoRequest dto) {
        return servicio.aplicarDescuento(dto);
    }

    /** POST /api/productos/descuento/limpiar — quita el descuento de un lote. */
    @PostMapping("/descuento/limpiar")
    @PreAuthorize("hasAuthority('PERMISO_PRODUCTOS_GESTIONAR')")
    public List<ProductoResponse> quitarDescuento(
            @Valid @RequestBody QuitarDescuentoRequest dto) {
        return servicio.quitarDescuento(dto.productoIds());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISO_PRODUCTOS_GESTIONAR')")
    public ResponseEntity<Void> eliminar(@PathVariable @Positive Long id) {
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
