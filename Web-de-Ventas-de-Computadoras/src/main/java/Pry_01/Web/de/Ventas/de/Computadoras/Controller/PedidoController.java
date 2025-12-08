package Pry_01.Web.de.Ventas.de.Computadoras.Controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import Pry_01.Web.de.Ventas.de.Computadoras.Dto.PedidoDTO.PedidoResponseDTO;
import Pry_01.Web.de.Ventas.de.Computadoras.Dto.PedidoDTO.Mapper.PedidoMapper;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.PedidoModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Model.UsuarioModel;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.PedidoService;
import Pry_01.Web.de.Ventas.de.Computadoras.Service.UsuarioService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService pedidoService;
    private final UsuarioService usuarioService;
    private final PedidoMapper pedidoMapper;

    @GetMapping
    public ResponseEntity<List<PedidoResponseDTO>> listarTodos() {
        List<PedidoResponseDTO> lista = pedidoService.listarPedido()
                .stream()
                .map(pedidoMapper::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(lista);
    }

    
    @GetMapping("/{id}")
    public ResponseEntity<PedidoResponseDTO> obtenerPorId(@PathVariable Long id) {
        return pedidoService.obtenerPedidoPorId(id)
                .map(pedido -> ResponseEntity.ok(pedidoMapper.toDto(pedido)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<PedidoResponseDTO>> listarPorUsuario(@PathVariable Long usuarioId) {

        UsuarioModel usuario = usuarioService.obtenerPorId(usuarioId);
        if (usuario == null) {
            return ResponseEntity.notFound().build();
        }

        List<PedidoResponseDTO> pedidos = pedidoService.listarPedidosPorUsuario(usuario)
                .stream()
                .map(pedidoMapper::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(pedidos);
    }

    @PostMapping("/crear/{usuarioId}/{metodoPagoId}")
    public ResponseEntity<PedidoResponseDTO> crearPedido(
            @PathVariable Long usuarioId,
            @PathVariable Long metodoPagoId) {

        UsuarioModel usuario = usuarioService.obtenerPorId(usuarioId);
        if (usuario == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            PedidoModel pedido = pedidoService.crearPedidoDesdeCarrito(usuario, metodoPagoId);
            return ResponseEntity.ok(pedidoMapper.toDto(pedido));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<String> eliminar(@PathVariable Long id) {
        try {
            pedidoService.eliminarPedidoPorId(id);
            return ResponseEntity.ok("Pedido eliminado correctamente");
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }
}
