package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.CategoriaDTO.CategoriaCreateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.MarcaDTO.MarcaCreateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.MetodoPagoDTO.MetodoPagoCreateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.ProductosDTO.ProductosCreateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.ProductosDTO.ProductosUpdateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.UsuarioDTO.UsuarioDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.MarcaModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.ProductosDTO.ProductosResponseDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.CategoriaService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.MarcaService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.MetodoPagoService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.ProductoService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.UsuarioService;

@Slf4j
@Controller
@RequestMapping("/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService productoService;
    private final MarcaService marcaService;
    private final CategoriaService categoriaService;
    private final MetodoPagoService metodoPagoService;
    private final UsuarioService usuarioService;

    @GetMapping("/marca/{marcaId}")
    public String mostrarProductosPorMarca(@PathVariable Long marcaId,
            @RequestParam(defaultValue = "0") int page,
            Model model,
            HttpSession session) {

        MarcaModel marca = marcaService.obtenerPorId(marcaId);
        if (marca == null) {
            model.addAttribute("mensajeError", "Marca no encontrada.");
            return "error/404";
        }

        PageRequest pageable = PageRequest.of(page, 12);
        Page<ProductosResponseDTO> productosPage = productoService.listarPorMarcas(List.of(marcaId), pageable);

        UsuarioDTO usuarioDTO = (UsuarioDTO) session.getAttribute("usuario");

        model.addAttribute("usuario", usuarioDTO);
        model.addAttribute("marca", marca);
        model.addAttribute("productos", productosPage.getContent());
        model.addAttribute("currentPage", productosPage.getNumber());
        model.addAttribute("totalPages", productosPage.getTotalPages());
        model.addAttribute("totalElements", productosPage.getTotalElements());

        return "productosPorMarca";
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
            String busqueda = search.toLowerCase();
            productos = productos.stream()
                    .filter(p -> (p.getName() != null && p.getName().toLowerCase().contains(busqueda))
                            || (p.getDescription() != null && p.getDescription().toLowerCase().contains(busqueda)))
                    .toList();
        }

        UsuarioDTO usuarioDTO = (UsuarioDTO) session.getAttribute("usuario");
        model.addAttribute("usuario", usuarioDTO);
        model.addAttribute("productos", productos);
        model.addAttribute("marcas", marcaService.listarMarcas());

        return "redirect:/Index";
    }

    @PostMapping
    public String guardarProducto(@Valid @ModelAttribute("producto") ProductosCreateDTO dto,
            BindingResult result,
            Model model) {

        if (result.hasErrors()) {
            log.warn("Errores de validación al crear producto: {}", result.getAllErrors());
            model.addAttribute("seccion", "productos");
            model.addAttribute("usuarios", usuarioService.listarUsuario());
            model.addAttribute("usuario", new UsuarioModel());
            model.addAttribute("producto", dto);
            model.addAttribute("productos", productoService.listarProducto());
            model.addAttribute("categoria", new CategoriaCreateDTO());
            model.addAttribute("categorias", categoriaService.listarCategoria());
            model.addAttribute("marca", new MarcaCreateDTO());
            model.addAttribute("marcas", marcaService.listarMarcas());
            model.addAttribute("metodoPago", new MetodoPagoCreateDTO());
            model.addAttribute("metodoPagos", metodoPagoService.listarMetodosPago());

            return "admin/VistaAdmin";
        }

        try {
            productoService.guardarProducto(dto);
            log.info("Producto guardado exitosamente: {}", dto.getName());
        } catch (Exception e) {
            log.error("Error guardando producto: {}", dto, e);

            try {
                model.addAttribute("seccion", "productos");
                model.addAttribute("usuarios", usuarioService.listarUsuario());
                model.addAttribute("usuario", new UsuarioModel());
                model.addAttribute("producto", dto);
                model.addAttribute("productos", productoService.listarProducto());
                model.addAttribute("categoria", new CategoriaCreateDTO());
                model.addAttribute("categorias", categoriaService.listarCategoria());
                model.addAttribute("marca", new MarcaCreateDTO());
                model.addAttribute("marcas", marcaService.listarMarcas());
                model.addAttribute("metodoPago", new MetodoPagoCreateDTO());
                model.addAttribute("metodoPagos", metodoPagoService.listarMetodosPago());

                model.addAttribute("mensajeError", "No se pudo guardar el producto: " + e.getMessage());
                return "admin/VistaAdmin";
            } catch (Exception ex) {
                log.error("Error cargando datos del modelo", ex);
                throw new RuntimeException("Error al procesar la solicitud", ex);
            }
        }

        return "redirect:/VistaAdmin?seccion=productos";
    }

    @PostMapping("/editar/{id}")
    public String editarProducto(@PathVariable Long id,
            @Valid @ModelAttribute("producto") ProductosUpdateDTO dto,
            BindingResult result,
            Model model) {

        if (result.hasErrors()) {
            log.warn("Errores de validación al editar producto {}: {}", id, result.getAllErrors());
            model.addAttribute("seccion", "productos");
            model.addAttribute("usuarios", usuarioService.listarUsuario());
            model.addAttribute("usuario", new UsuarioModel());
            model.addAttribute("producto", new ProductosCreateDTO());
            model.addAttribute("productos", productoService.listarProducto());
            model.addAttribute("categoria", new CategoriaCreateDTO());
            model.addAttribute("categorias", categoriaService.listarCategoria());
            model.addAttribute("marca", new MarcaCreateDTO());
            model.addAttribute("marcas", marcaService.listarMarcas());
            model.addAttribute("metodoPago", new MetodoPagoCreateDTO());
            model.addAttribute("metodoPagos", metodoPagoService.listarMetodosPago());
            model.addAttribute("mensajeError", "Error al actualizar el producto");
            return "admin/VistaAdmin";
        }

        try {
            productoService.actualizarProducto(id, dto);
            log.info("Producto {} actualizado exitosamente", id);
        } catch (Exception e) {
            log.error("Error actualizando producto {}", id, e);
            model.addAttribute("seccion", "productos");
            model.addAttribute("usuarios", usuarioService.listarUsuario());
            model.addAttribute("usuario", new UsuarioModel());
            model.addAttribute("producto", new ProductosCreateDTO());
            model.addAttribute("productos", productoService.listarProducto());
            model.addAttribute("categoria", new CategoriaCreateDTO());
            model.addAttribute("categorias", categoriaService.listarCategoria());
            model.addAttribute("marca", new MarcaCreateDTO());
            model.addAttribute("marcas", marcaService.listarMarcas());
            model.addAttribute("metodoPago", new MetodoPagoCreateDTO());
            model.addAttribute("metodoPagos", metodoPagoService.listarMetodosPago());
            model.addAttribute("mensajeError", "Error al actualizar el producto: " + e.getMessage());
            return "admin/VistaAdmin";
        }

        return "redirect:/VistaAdmin?seccion=productos";
    }

    @PostMapping("/eliminar/{id}")
    public String eliminarProducto(@PathVariable Long id) {
        productoService.eliminarProducto(id);
        return "redirect:/VistaAdmin?seccion=productos";
    }

    @GetMapping("/{id}")
    @ResponseBody
    public ProductosResponseDTO obtenerProducto(@PathVariable Long id) {
        return productoService.obtenerProductoPorId(id);
    }

    @GetMapping("/admin/fragment")
    public String obtenerFragmentoAdmin() {
        return "fragments/Admin-gest/Gest-productos :: gest-productos";
    }
}
