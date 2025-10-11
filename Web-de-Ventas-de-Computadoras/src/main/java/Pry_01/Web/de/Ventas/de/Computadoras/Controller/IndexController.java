package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.CategoriaModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.CategoriaService;

@Controller
public class IndexController {
    private final CategoriaService categoriaService;

    public IndexController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping("/Index")
    public String mostrarIndex(Model model) {
        List<CategoriaModel> categorias = categoriaService.listarCategoria();
        model.addAttribute("categorias", categorias);
        return "Index";
    }

    @GetMapping("/header")
    public String mostrarHeader() {
    return "fragments/headerFooter/header";
    }
}
