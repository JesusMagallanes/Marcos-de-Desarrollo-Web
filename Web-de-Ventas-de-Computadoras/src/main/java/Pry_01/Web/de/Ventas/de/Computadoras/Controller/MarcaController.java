package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.CategoriaDTO.CategoriaCreateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.MarcaDTO.MarcaCreateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.MarcaDTO.MarcaResponseDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.MarcaDTO.MarcaUpdateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.MetodoPagoDTO.MetodoPagoCreateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.ProductosDTO.ProductosCreateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.CategoriaModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.CategoriaService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.MarcaService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.MetodoPagoService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.ProductoService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.UsuarioService;

import java.util.List;

@Slf4j
@Controller
@RequestMapping("/marcas")
@RequiredArgsConstructor
public class MarcaController {

    private final MarcaService marcaService;
    private final CategoriaService categoriaService;
    private final UsuarioService usuarioService;
    private final ProductoService productoService;
    private final MetodoPagoService metodoPagoService;

    @GetMapping("/api")
    @ResponseBody
    public List<MarcaResponseDTO> listarMarcasApi() {
        return marcaService.listarMarca();
    }

    @GetMapping("/categoria/{id}")
    public String mostrarMarcasPorCategoria(@PathVariable Long id,
                                            @RequestParam(defaultValue = "0") int page,
                                            Model model) {
        CategoriaModel categoria = categoriaService.obtenerPorId(id);
        if (categoria == null) {
            model.addAttribute("mensajeError", "Categoría no encontrada.");
            return "error/404";
        }

        PageRequest pageable = PageRequest.of(page, 12);
        Page<MarcaResponseDTO> marcasPage = marcaService.listarPorCategoria(categoria, pageable);

        model.addAttribute("categoria", categoria);
        model.addAttribute("categorias", categoriaService.listarCategoria());
        model.addAttribute("marcas", marcasPage.getContent());
        model.addAttribute("totalPages", marcasPage.getTotalPages());
        model.addAttribute("currentPage", marcasPage.getNumber());
        model.addAttribute("totalElements", marcasPage.getTotalElements());

        return "marcasCategoria";
    }

    @PostMapping
    public String guardarMarca(@Valid @ModelAttribute("marca") MarcaCreateDTO dto,
                               BindingResult result,
                               Model model) {
        log.info("Intentando guardar marca: {}", dto);

        if (result.hasErrors()) {
            log.warn("Errores de validación al crear marca: {}", result.getAllErrors());
            cargarDatosAdminPanel(model);
            model.addAttribute("seccion", "marca");
            model.addAttribute("marca", dto);
            return "admin/VistaAdmin";
        }

        try {
            marcaService.guardarMarca(dto);
            log.info("Marca guardada correctamente.");
        } catch (Exception e) {
            log.error("Error al guardar marca", e);
            cargarDatosAdminPanel(model);
            model.addAttribute("seccion", "marca");
            model.addAttribute("marca", dto);
            model.addAttribute("mensajeError", "No se pudo guardar la marca: " + e.getMessage());
            return "admin/VistaAdmin";
        }

        return "redirect:/VistaAdmin?seccion=marca";
    }

    @PostMapping("/editar/{id}")
    public String editarMarca(@PathVariable Long id,
                              @Valid @ModelAttribute("marca") MarcaUpdateDTO dto,
                              BindingResult result,
                              Model model) {
        if (result.hasErrors()) {
            model.addAttribute("marcas", marcaService.listarMarca());
            model.addAttribute("categorias", categoriaService.listarCategoria());
            return "fragments/Admin-gest/Gest-marcas :: gest-marcas";
        }

        try {
            marcaService.actualizarProducto(id, dto);
            log.info("Marca actualizada correctamente.");
        } catch (Exception e) {
            log.error("Error al actualizar marca", e);
            model.addAttribute("mensajeError", "No se pudo actualizar la marca: " + e.getMessage());
            return "fragments/Admin-gest/Gest-marcas :: gest-marcas";
        }

        return "redirect:/VistaAdmin?seccion=marca";
    }

    @PostMapping("/eliminar/{id}")
    public String eliminarMarca(@PathVariable Long id) {
        try {
            marcaService.eliminarProducto(id);
            log.info("Marca eliminada correctamente.");
        } catch (Exception e) {
            log.error("Error al eliminar marca", e);
        }
        return "redirect:/VistaAdmin?seccion=marca";
    }

    @GetMapping("/{id}")
    @ResponseBody
    public MarcaResponseDTO obtenerMarca(@PathVariable Long id) {
        return marcaService.obtenerProductoPorId(id);
    }

    @GetMapping("/admin/fragment")
    public String obtenerFragmentoAdmin() {
        return "fragments/Admin-gest/Gest-marcas :: gest-marcas";
    }

    private void cargarDatosAdminPanel(Model model) {
        model.addAttribute("usuarios", usuarioService.listarUsuario());
        model.addAttribute("usuario", new UsuarioModel());
        model.addAttribute("producto", new ProductosCreateDTO());
        model.addAttribute("productos", productoService.listarProducto());
        model.addAttribute("categoria", new CategoriaCreateDTO());
        model.addAttribute("categorias", categoriaService.listarCategoria());
        model.addAttribute("marca", new MarcaCreateDTO());
        model.addAttribute("marcas", marcaService.listarMarca());
        model.addAttribute("metodoPago", new MetodoPagoCreateDTO());
        model.addAttribute("metodoPagos", metodoPagoService.listarMetodosPago());
    }
}
