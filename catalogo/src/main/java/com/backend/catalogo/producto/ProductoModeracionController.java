package com.backend.catalogo.producto;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.backend.catalogo.producto.dto.ProductoDtos.ProductoResponse;
import com.backend.catalogo.producto.dto.ProductoDtos.RechazoProductoRequest;
import com.backend.catalogo.shared.seguridad.JwtUtils;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

/**
 * La cola de revisión de productos de colaborador.
 *
 * <p>Mismo patrón que {@code ValoracionModeracionController}: moderar es una
 * tarea del personal, con su propio prefijo y su propio permiso, y no un caso
 * particular del CRUD del catálogo.
 */
@RestController
@RequestMapping("/api/productos/moderacion")
@Validated
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERMISO_PRODUCTOS_GESTIONAR')")
public class ProductoModeracionController {

    private final ProductoService servicio;

    /**
     * La cola. Sin parámetro devuelve las pendientes, que es lo que hay que
     * mirar; con `estado` se pueden repasar las ya resueltas.
     */
    @GetMapping
    public List<ProductoResponse> cola(@RequestParam(required = false) EstadoModeracion estado) {
        return servicio.enModeracion(estado);
    }

    @PostMapping("/{id}/aprobar")
    public ProductoResponse aprobar(@PathVariable @Positive Long id,
            @AuthenticationPrincipal Jwt jwt) {
        return servicio.aprobarModeracion(id, JwtUtils.uidDe(jwt));
    }

    /** El motivo es obligatorio: se le enseña al colaborador para que corrija. */
    @PostMapping("/{id}/rechazar")
    public ProductoResponse rechazar(@PathVariable @Positive Long id,
            @Valid @RequestBody RechazoProductoRequest dto,
            @AuthenticationPrincipal Jwt jwt) {
        return servicio.rechazarModeracion(id, JwtUtils.uidDe(jwt), dto.motivo());
    }
}
