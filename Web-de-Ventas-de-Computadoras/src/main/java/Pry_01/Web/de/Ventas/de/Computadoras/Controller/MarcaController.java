package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import Pry_01.Web.de.Ventas.de.Computadoras.Dto.CategoriaDTO.CategoriaCreateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.MarcaDTO.MarcaCreateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.MarcaDTO.MarcaUpdateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.MetodoPagoDTO.MetodoPagoCreateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.ProductosDTO.ProductosCreateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.MarcaModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.CategoriaService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.MarcaService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.ProductoService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.UsuarioService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/marcas")
public class MarcaController {

    private final MarcaService marcaService;
    private final CategoriaService categoriaService;
    private final UsuarioService usuarioService;
    private final ProductoService productoService;

    public MarcaController(MarcaService marcaService, CategoriaService categoriaService, UsuarioService usuarioService, ProductoService productoService) {
        this.marcaService = marcaService;
        this.categoriaService = categoriaService;
        this.usuarioService = usuarioService;
        this.productoService = productoService;
    }

    // Listar marcas por API (opcional)
    @GetMapping("/api")
    @ResponseBody
    public List<MarcaModel> listarMarcasApi() {
        return marcaService.listarMarcas();
    }

    // Guardar nueva marca
    @PostMapping
    public String guardarMarca(@Valid @ModelAttribute("marca") MarcaCreateDTO dto,
                               BindingResult result,
                               Model model) {
        if (result.hasErrors()) {
            model.addAttribute("seccion", "marca");
            model.addAttribute("usuarios", usuarioService.listarUsuario());
            model.addAttribute("usuario", new UsuarioModel());
            model.addAttribute("producto", new ProductosCreateDTO());
            model.addAttribute("productos", productoService.listarProducto());
            model.addAttribute("categoria", new CategoriaCreateDTO());
            model.addAttribute("categorias", categoriaService.listarCategoria());
            model.addAttribute("marca", dto);
            model.addAttribute("marcas", marcaService.listarMarcas());
            model.addAttribute("metodoPago", new MetodoPagoCreateDTO());
            model.addAttribute("metodoPagos", new ArrayList<>());
            return "admin/VistaAdmin";
        }

        marcaService.guardarMarca(dto);
        return "redirect:/VistaAdmin?seccion=marca";
    }

    // Editar marca
    @PostMapping("/editar/{id}")
    public String editarMarca(@PathVariable Long id,
                              @Valid @ModelAttribute("marca") MarcaUpdateDTO dto,
                              BindingResult result,
                              Model model) {
        if (result.hasErrors()) {
            model.addAttribute("seccion", "marca");
            model.addAttribute("usuarios", usuarioService.listarUsuario());
            model.addAttribute("usuario", new UsuarioModel());
            model.addAttribute("producto", new ProductosCreateDTO());
            model.addAttribute("productos", productoService.listarProducto());
            model.addAttribute("categoria", new CategoriaCreateDTO());
            model.addAttribute("categorias", categoriaService.listarCategoria());
            model.addAttribute("marca", dto);
            model.addAttribute("marcas", marcaService.listarMarcas());
            model.addAttribute("metodoPago", new MetodoPagoCreateDTO());
            model.addAttribute("metodoPagos", new ArrayList<>());
            return "admin/VistaAdmin";
        }

        marcaService.actualizarMarca(id, dto);
        return "redirect:/VistaAdmin?seccion=marca";
    }

    // Eliminar marca
    @PostMapping("/eliminar/{id}")
    public String eliminarMarca(@PathVariable Long id) {
        marcaService.eliminarMarca(id);
        return "redirect:/VistaAdmin?seccion=marca";
    }

    // Obtener marca por id (para edición dinámica en frontend)
    @GetMapping("/{id}")
    @ResponseBody
    public MarcaModel obtenerMarca(@PathVariable Long id) {
        return marcaService.obtenerPorId(id);
    }

    // Listar marcas por categoría (útil para cargar select dependiente)
    @GetMapping("/categoria/{categoriaId}")
    @ResponseBody
    public List<MarcaModel> listarMarcasPorCategoria(@PathVariable Long categoriaId) {
        return marcaService.listarMarcasPorCategoria(categoriaId);
    }
}
