package com.backend.catalogo.valoracion;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.backend.catalogo.valoracion.dto.ValoracionDtos.CambioEstadoValoracion;
import com.backend.catalogo.valoracion.dto.ValoracionDtos.ValoracionAdminResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

/**
 * Moderación de valoraciones: el ADMINISTRADOR revisa lo que escriben los
 * clientes y decide si se publica. Ninguno de estos endpoints es de la tienda;
 * la tienda solo ve las reseñas APROBADA que salen de {@link ValoracionService}.
 *
 * La columna de `estado` la toca el panel; el cliente, al valorar, siempre la
 * deja en PENDIENTE.
 */
@RestController
@RequestMapping("/api/valoraciones")
@Validated
@RequiredArgsConstructor
public class ValoracionModeracionController {

    private final ValoracionService servicio;

    /** Todas las valoraciones, o solo las de un estado. Sin estado = todas. */
    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('PERMISO_VALORACIONES_GESTIONAR')")
    public List<ValoracionAdminResponse> listar(
            @RequestParam(required = false) EstadoValoracion estado) {
        return servicio.listarParaModeracion(estado);
    }

    /** Aprobar, rechazar o devolver a pendiente. */
    @PatchMapping("/admin/{id}/estado")
    @PreAuthorize("hasAuthority('PERMISO_VALORACIONES_GESTIONAR')")
    public ValoracionAdminResponse cambiarEstado(@PathVariable @Positive Long id,
            @Valid @RequestBody CambioEstadoValoracion dto) {
        return servicio.cambiarEstado(id, dto.estado());
    }

    /** Retirar una reseña (abusiva, duplicada, …). */
    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasAuthority('PERMISO_VALORACIONES_GESTIONAR')")
    public ResponseEntity<Void> eliminar(@PathVariable @Positive Long id) {
        servicio.eliminarComoAdmin(id);
        return ResponseEntity.noContent().build();
    }
}
