package com.backend.compras.pedido;

import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.backend.compras.pedido.dto.PedidoDtos.CambioEstado;
import com.backend.compras.pedido.dto.PedidoDtos.PedidoResponse;
import com.backend.compras.shared.security.UsuarioAutenticado;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService servicio;

    /** Sustituye a /pedidos/usuario/{id}: el usuario sale del token. */
    @GetMapping("/mios")
    public List<PedidoResponse> mios(UsuarioAutenticado usuario) {
        return servicio.misPedidos(usuario.id());
    }

    /**
     * ¿Compré este producto? Lo pregunta `catalogo` antes de dejar valorar.
     *
     * <p>El usuario sale del token, no de la URL: si se aceptara por parámetro,
     * cualquiera podría preguntar por las compras de otro y averiguar qué compró.
     *
     * <p>Devuelve un booleano y no la lista de pedidos a proposito: es lo minimo
     * que `catalogo` necesita saber, y no hay motivo para que conozca el historial
     * de compras de nadie.
     */
    @GetMapping("/compre/{productoId}")
    public Map<String, Boolean> compre(@PathVariable @Positive Long productoId,
            UsuarioAutenticado usuario) {
        return Map.of("comprado", servicio.comproElProducto(usuario.id(), productoId));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISO_PEDIDOS_GESTIONAR')")
    public List<PedidoResponse> listar(@RequestParam(required = false) EstadoPedido estado) {
        return servicio.listar(estado);
    }

    @GetMapping("/{id}")
    public PedidoResponse obtener(@PathVariable Long id, UsuarioAutenticado usuario) {
        return servicio.obtener(id, usuario);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('PERMISO_PEDIDOS_GESTIONAR')")
    public PedidoResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstado dto) {
        return servicio.cambiarEstado(id, dto.estado());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISO_PEDIDOS_GESTIONAR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
