package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.PedidoModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.PedidoService;
import jakarta.validation.Valid;


@RestController
@RequestMapping("/pedido")
public class PedidoController {
    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @GetMapping
    public List<PedidoModel> listarPedido() {
        return pedidoService.listarPedido();
    }

    @PostMapping
    public ResponseEntity<PedidoModel> crearPedido(@Valid @RequestBody PedidoModel pedido){
        PedidoModel nuevo = pedidoService.guardarPedido(pedido);
        return ResponseEntity.ok(nuevo);
    }

    @DeleteMapping("/{id}")
        public ResponseEntity<Void> eliminarProducto(@PathVariable Long id){
            pedidoService.eliminarPedidoPorId(id);
            return ResponseEntity.noContent().build();
        }
    
}
