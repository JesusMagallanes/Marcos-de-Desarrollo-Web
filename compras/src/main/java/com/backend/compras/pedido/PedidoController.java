package com.backend.compras.pedido;

import java.util.List;

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

    @GetMapping
    @PreAuthorize("hasAnyRole('EMPLEADO', 'ADMINISTRADOR')")
    public List<PedidoResponse> listar(@RequestParam(required = false) EstadoPedido estado) {
        return servicio.listar(estado);
    }

    @GetMapping("/{id}")
    public PedidoResponse obtener(@PathVariable Long id, UsuarioAutenticado usuario) {
        return servicio.obtener(id, usuario);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('EMPLEADO', 'ADMINISTRADOR')")
    public PedidoResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstado dto) {
        return servicio.cambiarEstado(id, dto.estado());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
