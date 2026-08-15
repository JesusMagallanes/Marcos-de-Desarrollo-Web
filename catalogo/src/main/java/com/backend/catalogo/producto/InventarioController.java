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
import com.backend.catalogo.shared.seguridad.ContextoRls;
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

    /*
     * Los tres movimientos de stock van marcados como sistema. El motivo no es
     * comodidad: mover stock NO es «editar un producto».
     *
     * Desde la V15 `producto` tiene Row Level Security, y su política de
     * escritura dice «solo el dueño o el personal», que es lo que impide que un
     * colaborador toque el producto de otro. Pero quien descuenta stock aquí es
     * un CLIENTE comprando, y el producto es de la tienda: con su contexto, la
     * política no deja tocar la fila, el UPDATE afecta a cero registros y la
     * compra se rompe.
     *
     * Lo que autoriza estas operaciones no es la propiedad del producto sino la
     * reserva: llegan con una referencia de saga emitida por `compras` y el
     * endpoint ya exige estar autenticado. Por eso se ejecutan con identidad de
     * sistema, y por eso la marca va AQUÍ y no dentro del servicio: sus métodos
     * son @Transactional, así que Spring pide la conexión —y con ella se fija el
     * contexto— antes de entrar en el cuerpo.
     */

    @PostMapping("/reservas/{referencia}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> reservar(@PathVariable String referencia,
            @Valid @RequestBody AjusteStockLote lote) {
        ContextoRls.comoSistema(() -> inventarioService.reservar(referencia, lote.lineas()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reservas/{referencia}/confirmar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> confirmar(@PathVariable String referencia) {
        ContextoRls.comoSistema(() -> inventarioService.confirmar(referencia));
        return ResponseEntity.noContent().build();
    }

    /** Compensación de la saga. */
    @PostMapping("/reservas/{referencia}/liberar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> liberar(@PathVariable String referencia) {
        ContextoRls.comoSistema(() -> inventarioService.liberar(referencia));
        return ResponseEntity.noContent().build();
    }
}
