package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.CategoriaModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.ProductosDTO.ProductosResponseDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.CategoriaService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.ProductoService;

import java.util.regex.Pattern;

@Controller
public class IndexController {

    private final CategoriaService categoriaService;
    private final ProductoService productoService;

    // Inyección por constructor (recomendada)
    public IndexController(CategoriaService categoriaService, ProductoService productoService) {
        this.categoriaService = categoriaService;
        this.productoService = productoService;
    }

    // Página principal ("/")
    @GetMapping("/")
    public String principal(Model model) {
        // Reutilizamos mostrarIndex para llenar modelo y devolver vista
        List<CategoriaModel> categorias = categoriaService.listarCategoria();
        model.addAttribute("categorias", categorias);
        return "Index";
    }

    // Si quieres URL específica para /Index también la puedes mantener
    @GetMapping("/Index")
    public String mostrarIndex(Model model) {
        List<CategoriaModel> categorias = categoriaService.listarCategoria();
        List<ProductosResponseDTO> productos = productoService.listarProducto();

        model.addAttribute("categorias", categorias);
        model.addAttribute("productos", productos);

        return "Index";
    }

    @GetMapping("/header")
    public String mostrarHeader() {
        // Si usas Thymeleaf y quieres el fragmento concreto: "fragments/headerFooter :: header"
        // return "fragments/headerFooter :: header";
        return "fragments/headerFooter/header";
    }

    @GetMapping("/Carrito")
    public String mostrarCarrito() {
        return "Carrito";
    }

    @GetMapping("/Somos")
    public String mostrarSomos() {
        return "Somos";
    }
     @GetMapping("/Canales")
    public String mostrarCanales() {
        return "Canales";
    }
    
    
    @GetMapping("/metodosPago")
    public String metodosPago() {
        return "metodosPago";
    }

    @GetMapping("/productosCategoria")
    public String mostrarProductoCategoria() {
        return "productosCategoria";
    }

    /**
     * Endpoint seguro para devolver fragmentos. Usa /fragment?path=fragments/...
     * Validaciones:
     *  - obligatoriamente debe empezar por "fragments/"
     *  - no puede contener ".." ni caracteres no permitidos
     */
    @GetMapping("/fragment")
    public String cargarFragmento(@RequestParam String path) {
        if (path == null) {
            return "error/403";
        }

        // Reglas de saneamiento:
        // - Debe comenzar con "fragments/"
        // - No debe contener ".."
        // - Solo permitir caracteres alfanuméricos, guiones, guión bajo y slash
        if (!path.startsWith("fragments/") || path.contains("..")) {
            return "error/403";
        }

        Pattern allowed = Pattern.compile("^[a-zA-Z0-9_\\-/]+$");
        if (!allowed.matcher(path).matches()) {
            return "error/403";
        }

        // Devuelve la vista solicitada (ej: "fragments/foo/bar")
        return path;
    }
}
