package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.CategoriaModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.CategoriaService;

@Controller
public class IndexController {

    private final CategoriaService categoriaService;
    private static final String[] USER = { "Canales", "Detalles", "Somos" };

    public IndexController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping("/")
    public String Principal() {
        return "redirect:/Index";
    }

    @GetMapping("/{view}")
    public String page(@PathVariable String view) {
        for (String u : USER) {
            if (u.equals(view))
                return view;
        }
        return "redirect:/Index";
    }

    @GetMapping("/Index")
    public String mostrarIndex(Model model) {
        List<CategoriaModel> categorias = categoriaService.listarCategoria();
        model.addAttribute("categorias", categorias);
        return "Index";
    }
}
