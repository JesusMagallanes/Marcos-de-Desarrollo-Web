package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import Pry_01.Web.de.Ventas.de.Computadoras.Service.UsuarioService;

@Controller
public class HomeController {

    private final UsuarioService usuarioService;

    public HomeController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping("/Somos")
    public String Somos() {
        return "Somos";
    }

    @GetMapping("/Canales")
    public String Canales() {
        return "Canales";
    }

    @GetMapping("/Carrito")
    public String Carrito() {
        return "Carrito";
    }

    @GetMapping("/EnviosPag")
    public String Envios() {
        return "EnviosPag";
    }

    @GetMapping("/VistaAdmin")
    public String vistaAdmin(Model model) {
        // Le pasamos la lista de usuarios desde el service
        model.addAttribute("usuarios", usuarioService.listarUsuario());
        return "VistaAdmin"; // plantilla principal
    }
}