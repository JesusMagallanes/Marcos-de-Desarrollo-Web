package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String mostrarIndex() {
        return "Index"; // templates/Index.html
    }

    @GetMapping("/Somos")
    public String mostrarSomos() {
        return "Somos";
    }

    @GetMapping("/Detalles")
    public String mostrarDetalles() {
        return "Detalles";
    }

    @GetMapping("/Canales")
    public String mostrarCanales() {
        return "Canales";
    }

    @GetMapping("/productosCategoria")
    public String mostrarProductosCategoria() {
        return "productosCategoria";
    }
    @GetMapping("/EnviosPag")
    public String mostrarEnviosPag() {
        return "EnviosPag";
    }

}
