package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpSession;

import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.UsuarioDTO.UsuarioDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.UsuarioService;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.CarritoModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.CarritoService;

@Controller
@RequestMapping("/carrito")
public class CarritoController {

    private final CarritoService carritoService;
    private final UsuarioService usuarioService;

    public CarritoController(CarritoService carritoService, UsuarioService usuarioService) {
        this.carritoService = carritoService;
        this.usuarioService = usuarioService;
    }

    // ➕ Agregar producto al carrito
    @PostMapping("/agregar/{productoId}")
    public String agregarAlCarrito(@PathVariable Long productoId, HttpSession session) {
        UsuarioDTO dto = (UsuarioDTO) session.getAttribute("usuario");
        if (dto == null) {
            return "redirect:/login";
        }
        UsuarioModel usuario = usuarioService.obtenerPorId(dto.getId());
        if (usuario == null) return "redirect:/login";

        carritoService.agregarProducto(usuario, productoId);
        return "redirect:/Index"; // o redirige a la misma página donde se agregó
    }

    // 🛒 Ver carrito (usado para la vista Carrito o el offcanvas)
    @GetMapping
    public String verCarrito(Model model, HttpSession session) {
        UsuarioDTO dto = (UsuarioDTO) session.getAttribute("usuario");
        if (dto == null) {
            return "redirect:/login";
        }
        UsuarioModel usuario = usuarioService.obtenerPorId(dto.getId());
        if (usuario == null) return "redirect:/login";

        CarritoModel carrito = carritoService.obtenerCarrito(usuario);
        model.addAttribute("carrito", carrito);
        model.addAttribute("total", carritoService.calcularTotal(usuario));

        return "Carrito"; 
    }

    // --- Endpoints AJAX para offcanvas/front-end ---
    @GetMapping("/items")
    @ResponseBody
    public Object obtenerItemsAjax(HttpSession session) {
        UsuarioDTO dto = (UsuarioDTO) session.getAttribute("usuario");
        if (dto == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No autorizado"));
        }
        UsuarioModel usuario = usuarioService.obtenerPorId(dto.getId());
        if (usuario == null) return ResponseEntity.status(401).body(Map.of("error", "No autorizado"));
        CarritoModel carrito = carritoService.obtenerCarrito(usuario);
        List<Map<String, Object>> items = carrito.getItems().stream().map(i -> {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("itemId", i.getId());
            m.put("productId", i.getProducto().getId());
            m.put("nombre", i.getProducto().getName());
            m.put("precio", i.getProducto().getPrecio());
            m.put("cantidad", i.getCantidad());
            m.put("image", i.getProducto().getImageUrl());
            return m;
        }).collect(Collectors.toList());
        return Map.of("items", items, "subtotal", carritoService.calcularTotal(usuario));
    }

    @PostMapping("/addAjax/{productoId}")
    @ResponseBody
    public Object agregarAjax(@PathVariable Long productoId, @RequestBody(required = false) Map<String, Object> body, HttpSession session) {
        UsuarioDTO dto = (UsuarioDTO) session.getAttribute("usuario");
        if (dto == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No autorizado"));
        }
        UsuarioModel usuario = usuarioService.obtenerPorId(dto.getId());
        if (usuario == null) return ResponseEntity.status(401).body(Map.of("error", "No autorizado"));
        int cantidad = 1;
        if (body != null && body.get("cantidad") != null) {
            try { cantidad = Integer.parseInt(String.valueOf(body.get("cantidad"))); } catch (Exception ex) { cantidad = 1; }
        }
        carritoService.agregarProductoConCantidad(usuario, productoId, cantidad);
        return Map.of("ok", true);
    }

    @PostMapping("/removeAjax/{itemId}")
    @ResponseBody
    public Object removeAjax(@PathVariable Long itemId, HttpSession session) {
        UsuarioDTO dto = (UsuarioDTO) session.getAttribute("usuario");
        if (dto == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No autorizado"));
        }
        UsuarioModel usuario = usuarioService.obtenerPorId(dto.getId());
        if (usuario == null) return ResponseEntity.status(401).body(Map.of("error", "No autorizado"));
        carritoService.eliminarItem(itemId);
        return Map.of("ok", true);
    }

    @GetMapping("/eliminar/{itemId}")
    public String eliminarItem(@PathVariable Long itemId, HttpSession session) {
        UsuarioDTO dto = (UsuarioDTO) session.getAttribute("usuario");
        if (dto == null) {
            return "redirect:/login";
        }
        UsuarioModel usuario = usuarioService.obtenerPorId(dto.getId());
        if (usuario == null) return "redirect:/login";

        carritoService.eliminarItem(itemId);
        return "redirect:/carrito";
    }

   
    @GetMapping("/vaciar")
    public String vaciarCarrito(HttpSession session) {
        UsuarioDTO dto = (UsuarioDTO) session.getAttribute("usuario");
        if (dto == null) {
            return "redirect:/login";
        }
        UsuarioModel usuario = usuarioService.obtenerPorId(dto.getId());
        if (usuario == null) return "redirect:/login";

        carritoService.vaciarCarrito(usuario);
        return "redirect:/carrito";
    }
}
