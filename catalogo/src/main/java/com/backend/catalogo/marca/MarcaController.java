package com.backend.catalogo.marca;

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

import com.backend.catalogo.marca.dto.MarcaDtos.MarcaRequest;
import com.backend.catalogo.marca.dto.MarcaDtos.MarcaResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/marcas")
@Validated
@RequiredArgsConstructor
public class MarcaController {

    private final MarcaService servicio;

    @GetMapping
    public List<MarcaResponse> listar() {
        return servicio.listar();
    }

    @GetMapping("/categoria/{categoriaId}")
    public List<MarcaResponse> porCategoria(@PathVariable @Positive Long categoriaId) {
        return servicio.listarPorCategoria(categoriaId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERMISO_MARCAS_GESTIONAR')")
    public ResponseEntity<MarcaResponse> crear(@Valid @RequestBody MarcaRequest dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISO_MARCAS_GESTIONAR')")
    public MarcaResponse actualizar(@PathVariable @Positive Long id, @Valid @RequestBody MarcaRequest dto) {
        return servicio.actualizar(id, dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISO_MARCAS_GESTIONAR')")
    public ResponseEntity<Void> eliminar(@PathVariable @Positive Long id) {
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
