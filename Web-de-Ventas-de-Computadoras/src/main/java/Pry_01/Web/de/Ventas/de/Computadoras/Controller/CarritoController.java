package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.CarritoModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.CarritoService;

@Controller
@RequestMapping("/carrito")
public class CarritoController {

    private final CarritoService carritoService;

    public CarritoController(CarritoService carritoService) {
        this.carritoService = carritoService;
    }

    // ➕ Agregar producto al carrito
    @PostMapping("/agregar/{productoId}")
    public String agregarAlCarrito(@PathVariable Long productoId, HttpSession session) {
        UsuarioModel usuario = (UsuarioModel) session.getAttribute("usuarioLogeado");
        if (usuario == null) {
            return "redirect:/login";
        }

        carritoService.agregarProducto(usuario, productoId);
        return "redirect:/Index"; // o redirige a la misma página donde se agregó
    }

    // 🛒 Ver carrito (usado para la vista Carrito o el offcanvas)
    @GetMapping
    public String verCarrito(Model model, HttpSession session) {
        UsuarioModel usuario = (UsuarioModel) session.getAttribute("usuarioLogeado");
        if (usuario == null) {
            return "redirect:/login";
        }

        CarritoModel carrito = carritoService.obtenerCarrito(usuario);
        model.addAttribute("carrito", carrito);
        model.addAttribute("total", carritoService.calcularTotal(usuario));

        return "Carrito"; 
    }

    @GetMapping("/eliminar/{itemId}")
    public String eliminarItem(@PathVariable Long itemId, HttpSession session) {
        UsuarioModel usuario = (UsuarioModel) session.getAttribute("usuarioLogeado");
        if (usuario == null) {
            return "redirect:/login";
        }

        carritoService.eliminarItem(itemId);
        return "redirect:/carrito";
    }

   
    @GetMapping("/vaciar")
    public String vaciarCarrito(HttpSession session) {
        UsuarioModel usuario = (UsuarioModel) session.getAttribute("usuarioLogeado");
        if (usuario == null) {
            return "redirect:/login";
        }

        carritoService.vaciarCarrito(usuario);
        return "redirect:/carrito";
    }
}
