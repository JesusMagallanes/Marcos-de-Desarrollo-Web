package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import Pry_01.Web.de.Ventas.de.Computadoras.Model.EstadoPedido;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.PedidoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/envios")
@RequiredArgsConstructor
public class EnvioController {

    private final PedidoService pedidoService;

    @GetMapping
    public String gestionarEnvios(Model model) {
        // Mapeamos los estados del pedido a las pestañas de envíos
        // Pendientes de envío = Pedidos PAGADOS (o PENDIENTES si no hay flujo de pago
        // real)
        var pendientes = pedidoService.listarPedidosPorEstado(EstadoPedido.PENDIENTE);
        // Si tienes estado PAGADO, úsalo aquí en lugar de PENDIENTE, o ambos.
        // Por ahora usaremos PENDIENTE como "Por enviar" según tu flujo actual.

        var enCamino = pedidoService.listarPedidosPorEstado(EstadoPedido.EN_TRANSITO);
        var entregados = pedidoService.listarPedidosPorEstado(EstadoPedido.ENTREGADO);

        System.out.println("DEBUG: Pendientes encontrados: " + pendientes.size());
        System.out.println("DEBUG: En Camino encontrados: " + enCamino.size());
        System.out.println("DEBUG: Entregados encontrados: " + entregados.size());

        model.addAttribute("enviosPendientes", pendientes);
        model.addAttribute("enviosEnCamino", enCamino);
        model.addAttribute("enviosEntregados", entregados);
        return "EnviosPag";
    }

}
