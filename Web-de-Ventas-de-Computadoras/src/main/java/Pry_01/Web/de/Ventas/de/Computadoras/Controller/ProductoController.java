package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.CategoriaDTO.CategoriaCreateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.ProductosDTO.ProductosCreateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.ProductosDTO.ProductosUpdateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.UsuarioDTO.UsuarioDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.CategoriaModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.ProductosDTO.ProductosResponseDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.CategoriaService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.ProductoService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.UsuarioService;

@Slf4j
@Controller
@RequestMapping("/productos")
public class ProductoController {

    private final ProductoService productoService;
    private final CategoriaService categoriaService;
    private final UsuarioService usuarioService;

    public ProductoController(ProductoService productoService, CategoriaService categoriaService,
            UsuarioService usuarioService) {
        this.productoService = productoService;
        this.categoriaService = categoriaService;
        this.usuarioService = usuarioService;
    }

    @GetMapping("/categoria/{slug}")
    public String mostrarProductosPorCategoria(@PathVariable String slug,
            @RequestParam(defaultValue = "0") int page,
            Model model, HttpSession session, UsuarioModel usuario) {
        CategoriaModel categoria = categoriaService.obtenerPorSlug(slug);
        if (categoria == null) {
            model.addAttribute("mensajeError", "Categoría no encontrada.");
            return "error/404";
        }

        PageRequest pageable = PageRequest.of(page, 12); // 12 productos por página
        Page<ProductosResponseDTO> productosPage = productoService.listarPorCategoria(categoria, pageable);
        UsuarioDTO usuarioDTO = (UsuarioDTO) session.getAttribute("usuario");
        model.addAttribute("usuario", usuarioDTO);
        model.addAttribute("categoria", categoria);
        model.addAttribute("categorias", categoriaService.listarCategoria());
        model.addAttribute("productos", productosPage.getContent());
        model.addAttribute("totalPages", productosPage.getTotalPages());
        model.addAttribute("currentPage", productosPage.getNumber());
        model.addAttribute("totalElements", productosPage.getTotalElements());
        return "productosCategoria";
    }

    @GetMapping("/api")
    @ResponseBody
    public List<ProductosResponseDTO> listarProductosApi() {
        return productoService.listarProducto();
    }

    @GetMapping
    public String listarProductos(Model model) {
        model.addAttribute("producto", new ProductosCreateDTO());
        model.addAttribute("productos", productoService.listarProducto());
        model.addAttribute("categorias", categoriaService.listarCategoria());
        return "fragments/Admin-gest/Gest-productos :: gest-productos";
    }

    @PostMapping
    public String guardarProducto(@Valid @ModelAttribute("producto") ProductosCreateDTO dto,
            BindingResult result,
            Model model) {
        log.info("Intentando guardar producto: {}", dto);

        if (result.hasErrors()) {
            log.warn("Errores de validación al guardar producto: {}", result.getAllErrors());

            model.addAttribute("seccion", "producto");
            model.addAttribute("usuarios", usuarioService.listarUsuario());
            model.addAttribute("usuario", new UsuarioModel());
            model.addAttribute("producto", dto);
            model.addAttribute("productos", productoService.listarProducto());
            model.addAttribute("categoria", new CategoriaCreateDTO());
            model.addAttribute("categorias", categoriaService.listarCategoria());

            return "admin/VistaAdmin";
        }

        try {
            productoService.guardarProducto(dto);
            log.info("Producto guardado correctamente.");
        } catch (Exception e) {
            log.error("Error al guardar producto", e);

            model.addAttribute("seccion", "producto");
            model.addAttribute("usuarios", usuarioService.listarUsuario());
            model.addAttribute("usuario", new UsuarioModel());
            model.addAttribute("producto", dto);
            model.addAttribute("productos", productoService.listarProducto());
            model.addAttribute("categoria", new CategoriaCreateDTO());
            model.addAttribute("categorias", categoriaService.listarCategoria());

            model.addAttribute("mensajeError", "No se pudo guardar el producto: " + e.getMessage());
            return "admin/VistaAdmin";
        }

        return "redirect:/VistaAdmin";
    }

    @PostMapping("/editar/{id}")
    public String editarProducto(@PathVariable Long id,
            @Valid @ModelAttribute("producto") ProductosUpdateDTO dto,
            BindingResult result,
            Model model) {
        if (result.hasErrors()) {
            model.addAttribute("productos", productoService.listarProducto());
            model.addAttribute("categorias", categoriaService.listarCategoria());
            return "fragments/Admin-gest/Gest-productos :: gest-productos";
        }
        productoService.actualizarProducto(id, dto);
        return "redirect:/VistaAdmin";
    }

    @PostMapping("/eliminar/{id}")
    public String eliminarProducto(@PathVariable Long id) {
        productoService.eliminarProducto(id);
        return "redirect:/VistaAdmin";
    }

    @GetMapping("/{id}")
    @ResponseBody
    public ProductosResponseDTO obtenerProducto(@PathVariable Long id) {
        return productoService.obtenerProductoPorId(id);
    }
}