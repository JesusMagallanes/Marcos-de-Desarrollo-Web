package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.CategoriaModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.CategoriaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@Controller
@RequestMapping("/categoria")
public class CategoriaController {
    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping
    public String listarCategorias (Model model){
        model.addAttribute("Categoria", new CategoriaModel());
        model.addAttribute("categorias", categoriaService.listarCategoria());
        return "fragments/Admin-gest/Gest-categorias :: gest-categorias";
    }

    public String guardarCategoria(@ModelAttribute("Categoria") @Valid CategoriaModel categoriaModel) {
        categoriaService.crearCategoria(categoriaModel);
        return "redirect:/VistaAdmin";
    }

    @PostMapping("/editar/{id}")
    public String editarUsuario(@PathVariable Long id, @ModelAttribute("categoria") CategoriaModel categoria) {
        categoria.setId(id);
        categoriaService.actualizarCategoria(categoria);
        return "redirect:/VistaAdmin";
    }
    

}
