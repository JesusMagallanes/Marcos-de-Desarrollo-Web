package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

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
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.CategoriaDTO.CategoriaUpdateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.ProductosDTO.ProductosCreateDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.CategoriaModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.CategoriaService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.ProductoService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.UsuarioService;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/categorias")
public class CategoriaController {
    private final UsuarioService usuarioService;
    private final CategoriaService categoriaService;
    private final ProductoService productoService;

    public CategoriaController(CategoriaService categoriaService, UsuarioService usuarioService,
            ProductoService productoService) {
        this.categoriaService = categoriaService;
        this.usuarioService = usuarioService;
        this.productoService = productoService;
    }

    @GetMapping("/api")
    @ResponseBody
    public List<CategoriaModel> listarCategoriasApi() {
        
        return categoriaService.listarCategoria();
    }

    @PostMapping
    public String guardarCategoria(@Valid @ModelAttribute("categoria") CategoriaCreateDTO dto, BindingResult result,
            Model model) {
        if (result.hasErrors()) {
            model.addAttribute("seccion", "categoria");
            model.addAttribute("usuarios", usuarioService.listarUsuario());
            model.addAttribute("usuario", new UsuarioModel());
            model.addAttribute("producto", new ProductosCreateDTO());
            model.addAttribute("productos", productoService.listarProducto());
            model.addAttribute("categoria", dto);
            model.addAttribute("categorias", categoriaService.listarCategoria());
            return "admin/VistaAdmin";
        }
        categoriaService.guardarCategoria(dto);
        return "redirect:/VistaAdmin";
    }

    @PostMapping("/eliminar/{id}")
    public String eliminarCategoria(@PathVariable Long id) {
        categoriaService.eliminarCategoria(id);
        return "redirect:/VistaAdmin";
    }

    @PostMapping("/editar/{id}")
    public String editarCategoria(@PathVariable Long id,
            @Valid @ModelAttribute("categoria") CategoriaUpdateDTO dto,
            BindingResult result,
            Model model) {
        if (result.hasErrors()) {
            model.addAttribute("seccion", "categoria");
            model.addAttribute("usuarios", usuarioService.listarUsuario());
            model.addAttribute("usuario", new UsuarioModel());
            model.addAttribute("producto", new ProductosCreateDTO());
            model.addAttribute("productos", productoService.listarProducto());
            model.addAttribute("categoria", dto);
            model.addAttribute("categorias", categoriaService.listarCategoria());
            return "admin/VistaAdmin";
        }

        categoriaService.actualizarCategoria(id, dto);
        return "redirect:/VistaAdmin";
    }
}
