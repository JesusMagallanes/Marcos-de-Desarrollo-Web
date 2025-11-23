package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.CategoriaModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.ProductosDTO.ProductosResponseDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.CategoriaService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.ProductoService;
import jakarta.servlet.http.HttpSession;

@Controller
public class IndexController {
private final CategoriaService categoriaService;
    private final ProductoService productoService;

    // Inyección por constructor (recomendada)
    public IndexController(CategoriaService categoriaService, ProductoService productoService) {
        this.categoriaService = categoriaService;
        this.productoService = productoService;
    }
    @ModelAttribute
    public void addUsuarioToModel(HttpSession session, Model model) {
        var usuario = session.getAttribute("usuario");
        if (usuario != null) {
            model.addAttribute("usuario", usuario);
        }
    }
    
    // Página principal ("/")
    @GetMapping({"/", "/Index"})
    public String mostrarIndex(Model model) {
        List<CategoriaModel> categorias = categoriaService.listarCategoria();
        List<ProductosResponseDTO> productos = productoService.listarProducto();
        model.addAttribute("categorias", categorias);
        model.addAttribute("productos", productos);
        return "Index";
    }

    @GetMapping("/EnviosPag")
    public String enviosPag() {
        return "EnviosPag";
    }

    @GetMapping("/header")
    public String mostrarHeader() {
        // Si usas Thymeleaf y quieres el fragmento concreto: "fragments/headerFooter ::
        // header"
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
    @GetMapping("/canales")
    public String mostrarCanales() {
        return "Canales";
    }

    @GetMapping("/Canales")
    public String canales(){
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

    @GetMapping("/fragment")
    public String cargarFragmento(@RequestParam("name") String name) {
        String path = "fragments/LogginUserFiles/" + name;

        if (name == null || name.contains("..")) {
            return "error/403";
        }
        
        return path;
    }

    @GetMapping("/fragment/cuenta")
    public String cargarFragmentoCuenta(HttpSession session, Model model) {
        var usuario = session.getAttribute("usuario");
        model.addAttribute("usuario", usuario);
        return "fragments/LogginUserFiles/cuenta :: cuentaFragment";
    }

    @GetMapping("/Detalles")
    public String mostrarDetalles() {
        return "Detalles";
    }
}
