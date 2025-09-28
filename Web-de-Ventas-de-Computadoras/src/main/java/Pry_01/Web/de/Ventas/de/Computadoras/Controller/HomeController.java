package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

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

    @GetMapping("/Detalles")
    public String Detalles() {
        return "Detalles";
    }

}
