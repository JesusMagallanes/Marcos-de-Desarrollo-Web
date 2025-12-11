package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.PedidoDTO.PedidoResponseDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.PedidoDTO.Mapper.PedidoMapper;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.PedidoModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.PedidoService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.UsuarioService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Controller // Cambiado de @RestController a @Controller
@RequestMapping("/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService pedidoService;
    private final UsuarioService usuarioService;
    private final PedidoMapper pedidoMapper;

    // Vista para listar todos los pedidos (para un admin, por ejemplo)
    @GetMapping
    public String listarTodos(Model model) {
        List<PedidoResponseDTO> lista = pedidoService.listarPedido()
                .stream()
                .map(pedidoMapper::toDto)
                .collect(Collectors.toList());

        model.addAttribute("pedidos", lista);
        return "pedidos/lista_pedidos"; // Devuelve una vista HTML, ej: "lista_pedidos.html"
    }

    // Vista para ver el detalle de un pedido específico
    @GetMapping("/{id}")
    public String obtenerPorId(@PathVariable Long id, Model model) {
        return pedidoService.obtenerPedidoPorId(id)
                .map(pedido -> {
                    model.addAttribute("pedido", pedidoMapper.toDto(pedido));
                    return "pedidos/detalle_pedido"; // Devuelve una vista HTML, ej: "detalle_pedido.html"
                })
                .orElse("error/404"); // O una página de error si no se encuentra
    }

    // Vista para listar los pedidos de un usuario específico
    @GetMapping("/usuario/{usuarioId}")
    public String listarPorUsuario(@PathVariable Long usuarioId, Model model) {
        UsuarioModel usuario = usuarioService.obtenerPorId(usuarioId);
        if (usuario == null) {
            return "error/404"; // Página de error si el usuario no existe
        }

        List<PedidoResponseDTO> pedidos = pedidoService.listarPedidosPorUsuario(usuario)
                .stream()
                .map(pedidoMapper::toDto)
                .collect(Collectors.toList());

        model.addAttribute("pedidos", pedidos);
        model.addAttribute("usuario", usuario);
        return "pedidos/pedidos_usuario"; // Devuelve una vista HTML, ej: "pedidos_usuario.html"
    }

    // Acción para crear un pedido, luego redirige
    @PostMapping("/crear/{usuarioId}/{metodoPagoId}")
    public String crearPedido(
            @PathVariable Long usuarioId,
            @PathVariable Long metodoPagoId,
            RedirectAttributes redirectAttributes) {

        UsuarioModel usuario = usuarioService.obtenerPorId(usuarioId);
        if (usuario == null) {
            redirectAttributes.addFlashAttribute("error", "Usuario no encontrado.");
            return "redirect:/carrito"; // Redirige a alguna página de origen
        }

        try {
            PedidoModel nuevoPedido = pedidoService.crearPedidoDesdeCarrito(usuario, metodoPagoId);
            redirectAttributes.addFlashAttribute("success", "Pedido #" + nuevoPedido.getId() + " creado con éxito.");
            // Redirige a la página de detalle del nuevo pedido
            return "redirect:/pedidos/" + nuevoPedido.getId();
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/carrito";
        }
    }

    // Acción para eliminar un pedido, luego redirige
    @PostMapping("/eliminar/{id}") // Cambiado a POST para seguir buenas prácticas
    public String eliminar(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            pedidoService.eliminarPedidoPorId(id);
            redirectAttributes.addFlashAttribute("success", "Pedido #" + id + " eliminado correctamente.");
            return "redirect:/pedidos"; // Redirige a la lista de pedidos
        } catch (EntityNotFoundException e) {
            redirectAttributes.addFlashAttribute("error", "No se encontró el pedido a eliminar.");
            return "redirect:/pedidos";
        }
    }

    @PostMapping("/actualizar-estado/{id}")
    public String actualizarEstado(@PathVariable Long id,
            @RequestParam("estado") Pry_01.Web.de.Ventas.de.Computadoras.Model.EstadoPedido estado,
            RedirectAttributes redirectAttributes) {
        System.out.println("DEBUG: PedidoController - Actualizando estado. ID: " + id + ", Nuevo Estado: " + estado);
        try {
            pedidoService.actualizarEstado(id, estado);
            redirectAttributes.addFlashAttribute("mensaje", "Estado del pedido #" + id + " actualizado correctamente.");
            redirectAttributes.addFlashAttribute("tipoMensaje", "success");
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("mensaje", "Error al actualizar el estado: " + e.getMessage());
            redirectAttributes.addFlashAttribute("tipoMensaje", "danger");
        }
        return "redirect:/envios";
    }
}
