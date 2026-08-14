package com.backend.catalogo.guia;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backend.catalogo.guia.dto.GuiaDtos.GuiaRequest;
import com.backend.catalogo.guia.dto.GuiaDtos.GuiaResponse;
import com.backend.catalogo.guia.dto.GuiaDtos.GuiaResumen;
import com.backend.catalogo.shared.validacion.Limites;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

/**
 * Guías de ayuda ("Aprende con nosotros").
 *
 * Lectura pública de lo publicado; escribirlas y ver los borradores es cosa del
 * ADMINISTRADOR. Las rutas de gestión cuelgan de /admin para que la separación
 * se vea en la propia URL y no dependa solo de la anotación.
 */
@RestController
@RequestMapping("/api/guias")
@Validated
@RequiredArgsConstructor
public class GuiaController {

    private final GuiaService servicio;

    /* ── Público ── */

    @GetMapping
    public List<GuiaResumen> listar() {
        return servicio.listarPublicadas();
    }

    @GetMapping("/{slug}")
    public GuiaResponse obtener(
            @PathVariable @Pattern(regexp = Limites.SLUG, message = "Slug no válido") String slug) {
        return servicio.obtenerPublicada(slug);
    }

    /* ── Panel de administración ── */

    @GetMapping("/admin/todas")
    @PreAuthorize("hasAuthority('PERMISO_GUIAS_GESTIONAR')")
    public List<GuiaResumen> listarTodas() {
        return servicio.listarTodas();
    }

    @GetMapping("/admin/{slug}")
    @PreAuthorize("hasAuthority('PERMISO_GUIAS_GESTIONAR')")
    public GuiaResponse obtenerParaEdicion(
            @PathVariable @Pattern(regexp = Limites.SLUG, message = "Slug no válido") String slug) {
        return servicio.obtenerParaEdicion(slug);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERMISO_GUIAS_GESTIONAR')")
    public ResponseEntity<GuiaResponse> crear(@Valid @RequestBody GuiaRequest dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISO_GUIAS_GESTIONAR')")
    public GuiaResponse actualizar(@PathVariable @Positive Long id,
            @Valid @RequestBody GuiaRequest dto) {
        return servicio.actualizar(id, dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISO_GUIAS_GESTIONAR')")
    public ResponseEntity<Void> eliminar(@PathVariable @Positive Long id) {
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
