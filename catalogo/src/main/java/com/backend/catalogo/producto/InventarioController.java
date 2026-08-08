package com.backend.catalogo.producto;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backend.catalogo.inventario.InventarioService;
import com.backend.catalogo.producto.dto.ProductoDtos.AjusteStockLote;
import com.backend.catalogo.producto.dto.ProductoDtos.LineaPrecio;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * API interna que consume el servicio de compras: es el participante de la saga del
 * lado del inventario.
 */
@RestController
@RequestMapping("/api/inventario")
@RequiredArgsConstructor
public class InventarioController {

    private final ProductoService productoService;
    private final InventarioService inventarioService;

    /** Precios y stock actuales. Es la fuente de verdad del total del carrito. */
    @PostMapping("/precios")
    @PreAuthorize("isAuthenticated()")
    public List<LineaPrecio> precios(
            @RequestBody @NotEmpty @Size(max = 200, message = "Demasiados productos en una consulta") List<Long> productoIds) {
        return productoService.precios(productoIds);
    }

    /* ── Participante de la saga ── */

    @PostMapping("/reservas/{referencia}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> reservar(@PathVariable String referencia,
            @Valid @RequestBody AjusteStockLote lote) {
        inventarioService.reservar(referencia, lote.lineas());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reservas/{referencia}/confirmar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> confirmar(@PathVariable String referencia) {
        inventarioService.confirmar(referencia);
        return ResponseEntity.noContent().build();
    }

    /** Compensación de la saga. */
    @PostMapping("/reservas/{referencia}/liberar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> liberar(@PathVariable String referencia) {
        inventarioService.liberar(referencia);
        return ResponseEntity.noContent().build();
    }
}
