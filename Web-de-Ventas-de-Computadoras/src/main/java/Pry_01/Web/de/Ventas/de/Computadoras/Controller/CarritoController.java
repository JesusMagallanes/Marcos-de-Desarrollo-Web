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
import Pry_01.Web.de.Ventas.de.Computadoras.Model.PedidoModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.MetodoPagoModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.UsuarioDTO.UsuarioDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.UsuarioService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.PedidoService;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.CarritoModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.CarritoService;
import Pry_01.Web.de.Ventas.de.Computadoras.Repository.MetodoPagoRepository;

@Controller
@RequestMapping("/carrito")
public class CarritoController {

    private final CarritoService carritoService;
    private final UsuarioService usuarioService;
    private final PedidoService pedidoService;
    private final MetodoPagoRepository metodoPagoRepository;

    public CarritoController(CarritoService carritoService, UsuarioService usuarioService, PedidoService pedidoService, MetodoPagoRepository metodoPagoRepository) {
        this.carritoService = carritoService;
        this.usuarioService = usuarioService;
        this.pedidoService = pedidoService;
        this.metodoPagoRepository = metodoPagoRepository;
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

    /**
     * Endpoint para confirmar el pago y crear el pedido
     * Se llama después de que MercadoPago confirme el pago exitoso
     */
    @PostMapping("/confirmarPago")
    @ResponseBody
    public Object confirmarPago(HttpSession session) {
        try {
            System.out.println("=== INICIO confirmarPago ===");
            UsuarioDTO dto = (UsuarioDTO) session.getAttribute("usuario");
            if (dto == null) {
                System.out.println("ERROR: Usuario no encontrado en sesión");
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "No autorizado"));
            }
            System.out.println("Usuario en sesión: " + dto.getId());
            
            UsuarioModel usuario = usuarioService.obtenerPorId(dto.getId());
            if (usuario == null) {
                System.out.println("ERROR: Usuario no encontrado en BD");
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "No autorizado"));
            }
            System.out.println("Usuario encontrado: " + usuario.getName());

            // Obtener o crear el método de pago de MercadoPago
            MetodoPagoModel metodoPago = metodoPagoRepository.findAll().stream()
                .filter(m -> m.getName().toLowerCase().contains("mercadopago") || 
                            m.getName().toLowerCase().contains("mercado pago"))
                .findFirst()
                .orElseGet(() -> {
                    System.out.println("Método de pago MercadoPago no encontrado, creando uno nuevo");
                    MetodoPagoModel nuevoMetodo = new MetodoPagoModel();
                    nuevoMetodo.setName("MercadoPago");
                    nuevoMetodo.setDescription("Pago mediante MercadoPago");
                    return metodoPagoRepository.save(nuevoMetodo);
                });
            
            Long metodoPagoId = metodoPago.getId();
            System.out.println("Usando método de pago: " + metodoPago.getName() + " (ID: " + metodoPagoId + ")");
            
            PedidoModel pedido = pedidoService.crearPedidoDesdeCarrito(usuario, metodoPagoId);
            System.out.println("Pedido creado exitosamente con ID: " + pedido.getId());

            return ResponseEntity.ok(Map.of(
                "success", true, 
                "message", "Pedido creado exitosamente",
                "pedidoId", pedido.getId()
            ));
        } catch (Exception e) {
            System.out.println("ERROR al crear pedido: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "success", false, 
                "message", "Error al crear el pedido: " + e.getMessage()
            ));
        }
    }

    /**
     * Endpoint para obtener los pedidos del usuario
     */
    @GetMapping("/pedidos")
    @ResponseBody
    public Object obtenerPedidos(HttpSession session) {
        try {
            UsuarioDTO dto = (UsuarioDTO) session.getAttribute("usuario");
            if (dto == null) {
                return ResponseEntity.status(401).body(Map.of("error", "No autorizado"));
            }
            UsuarioModel usuario = usuarioService.obtenerPorId(dto.getId());
            if (usuario == null) return ResponseEntity.status(401).body(Map.of("error", "No autorizado"));

            List<PedidoModel> pedidos = pedidoService.listarPedidosPorUsuario(usuario);
            
            List<Map<String, Object>> pedidosData = pedidos.stream().map(pedido -> {
                Map<String, Object> p = new java.util.HashMap<>();
                p.put("id", pedido.getId());
                p.put("fecha", pedido.getCreadoEn().toString());
                p.put("estado", pedido.getEstado().toString());
                p.put("total", pedido.getTotal());
                p.put("metodoPago", pedido.getMetodoPago().getName());
                
                List<Map<String, Object>> detalles = pedido.getDetalles().stream().map(detalle -> {
                    Map<String, Object> d = new java.util.HashMap<>();
                    d.put("productoNombre", detalle.getProducto().getName());
                    d.put("cantidad", detalle.getCantidad());
                    d.put("precioUnitario", detalle.getPrecioUnitario());
                    d.put("total", detalle.getTotal());
                    d.put("imagen", detalle.getProducto().getImageUrl());
                    return d;
                }).collect(Collectors.toList());
                
                p.put("detalles", detalles);
                return p;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(Map.of("pedidos", pedidosData));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Error al obtener pedidos: " + e.getMessage()));
        }
    }
}
