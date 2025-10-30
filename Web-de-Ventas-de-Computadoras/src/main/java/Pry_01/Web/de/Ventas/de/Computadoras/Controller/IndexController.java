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
import jakarta.servlet.http.HttpSession;

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

    @GetMapping("/index")
    public String index(){
        return "Index";
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
<<<<<<< HEAD
    @GetMapping("/canales")
=======

    @GetMapping("/Canales")
>>>>>>> 38171a46e2edce450c628b8456df5379b94a61f7
    public String mostrarCanales() {
        return "Canales";
    }

<<<<<<< HEAD
    @GetMapping("/Canales")
    public String canales(){
        return "Canales";
    }

=======
>>>>>>> 38171a46e2edce450c628b8456df5379b94a61f7
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
