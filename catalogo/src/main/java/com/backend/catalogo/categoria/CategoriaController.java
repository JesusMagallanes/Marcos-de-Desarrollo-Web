package com.backend.catalogo.categoria;

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

import com.backend.catalogo.categoria.dto.CategoriaDtos.CategoriaRequest;
import com.backend.catalogo.categoria.dto.CategoriaDtos.CategoriaResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/categorias")
@Validated
@RequiredArgsConstructor
public class CategoriaController {

    private final CategoriaService servicio;

    @GetMapping
    public List<CategoriaResponse> listar() {
        return servicio.listar();
    }

    @GetMapping("/{id}")
    public CategoriaResponse obtener(@PathVariable @Positive Long id) {
        return servicio.obtener(id);
    }

    @GetMapping("/slug/{slug}")
    public CategoriaResponse porSlug(@PathVariable String slug) {
        return servicio.obtenerPorSlug(slug);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERMISO_CATEGORIAS_GESTIONAR')")
    public ResponseEntity<CategoriaResponse> crear(@Valid @RequestBody CategoriaRequest dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISO_CATEGORIAS_GESTIONAR')")
    public CategoriaResponse actualizar(@PathVariable @Positive Long id, @Valid @RequestBody CategoriaRequest dto) {
        return servicio.actualizar(id, dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISO_CATEGORIAS_GESTIONAR')")
    public ResponseEntity<Void> eliminar(@PathVariable @Positive Long id) {
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
