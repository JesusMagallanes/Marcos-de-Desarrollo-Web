package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.CategoriaDTO.CategoriaCreateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.MetodoPagoDTO.MetodoPagoCreateDTO;
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
    public String listarProductos(@RequestParam(name = "search", required = false) String search,
            Model model, HttpSession session) {

        List<ProductosResponseDTO> productos = productoService.listarProducto();
        if (search != null && !search.isBlank()) {
            String q = search.toLowerCase();
            productos = productos.stream()
                    .filter(p -> (p.getName() != null && p.getName().toLowerCase().contains(q))
                            || (p.getDescription() != null && p.getDescription().toLowerCase().contains(q)))
                    .toList();
        }

        UsuarioDTO usuarioDTO = (UsuarioDTO) session.getAttribute("usuario");
        model.addAttribute("usuario", usuarioDTO);

        List<CategoriaModel> categorias = categoriaService.listarCategoria();
        model.addAttribute("categorias", categorias);
        model.addAttribute("productos", productos);

        Map<Long, List<ProductosResponseDTO>> productosPorCategoria = new LinkedHashMap<>();
        for (CategoriaModel cat : categorias) {
            List<ProductosResponseDTO> filt = productos.stream()
                    .filter(p -> p.getCategoriaName() != null && p.getCategoriaName().equals(cat.getName()))
                    .toList();
            productosPorCategoria.put(cat.getId(), filt);
        }
        model.addAttribute("productosPorCategoria", productosPorCategoria);

        Map<Long, List<List<ProductosResponseDTO>>> productosPorCategoriaChunks = new LinkedHashMap<>();
        int chunkSize = 6;
        for (Map.Entry<Long, List<ProductosResponseDTO>> entry : productosPorCategoria.entrySet()) {
            List<ProductosResponseDTO> list = entry.getValue();
            List<List<ProductosResponseDTO>> chunks = new ArrayList<>();
            if (list != null && !list.isEmpty()) {
                for (int i = 0; i < list.size(); i += chunkSize) {
                    int end = Math.min(list.size(), i + chunkSize);
                    chunks.add(new ArrayList<>(list.subList(i, end)));
                }
            }
            productosPorCategoriaChunks.put(entry.getKey(), chunks);
        }
        model.addAttribute("productosPorCategoriaChunks", productosPorCategoriaChunks);

        // Devolver la vista pública (Index)
        return "redirect:/Index";
    }

    @PostMapping
    public String guardarProducto(@Valid @ModelAttribute("producto") ProductosCreateDTO dto,
            BindingResult result,
            Model model) {
        log.info("Intentando guardar producto: {}", dto);

        if (result.hasErrors()) {
            log.warn("Errores de validación al crear producto: {}", result.getAllErrors());
            model.addAttribute("seccion", "producto");
            model.addAttribute("usuarios", usuarioService.listarUsuario());
            model.addAttribute("usuario", new UsuarioModel());
            model.addAttribute("producto", dto);
            model.addAttribute("productos", productoService.listarProducto());
            model.addAttribute("categoria", new CategoriaCreateDTO());
            model.addAttribute("categorias", categoriaService.listarCategoria());
            model.addAttribute("metodoPago", new MetodoPagoCreateDTO());
            model.addAttribute("metodoPagos", new java.util.ArrayList<>());

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
            model.addAttribute("metodoPago", new MetodoPagoCreateDTO());
            model.addAttribute("metodoPagos", new java.util.ArrayList<>());

            model.addAttribute("mensajeError", "No se pudo guardar el producto: " + e.getMessage());
            return "admin/VistaAdmin";
        }

        return "redirect:/VistaAdmin?seccion=producto";
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

    @GetMapping("/admin/fragment")
    public String obtenerFragmentoAdmin() {
        // Endpoint para devolver explícitamente el fragmento del administrador.
        // Se protegerá a nivel de seguridad para que sólo administradores puedan
        // acceder.
        return "fragments/Admin-gest/Gest-productos :: gest-productos";
    }
}