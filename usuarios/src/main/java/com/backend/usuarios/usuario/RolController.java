package com.backend.usuarios.usuario;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backend.usuarios.usuario.dto.RolDtos.PermisoInfo;
import com.backend.usuarios.usuario.dto.RolDtos.RolCreate;
import com.backend.usuarios.usuario.dto.RolDtos.RolResponse;
import com.backend.usuarios.usuario.dto.RolDtos.RolUpdate;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RolController {

    private final RolService servicio;

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISO_ROLES_GESTIONAR')")
    public List<RolResponse> listar() {
        return servicio.listar();
    }

    /** Catálogo fijo de permisos del sistema, agrupado por módulo. */
    @GetMapping("/permisos")
    @PreAuthorize("hasAuthority('PERMISO_ROLES_GESTIONAR')")
    public List<PermisoInfo> catalogoPermisos() {
        return servicio.catalogoPermisos();
    }

    @GetMapping("/{nombre}")
    @PreAuthorize("hasAuthority('PERMISO_ROLES_GESTIONAR')")
    public RolResponse obtener(@PathVariable String nombre) {
        return servicio.obtener(nombre);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERMISO_ROLES_GESTIONAR')")
    public ResponseEntity<RolResponse> crear(@Valid @RequestBody RolCreate dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(dto));
    }

    @PutMapping("/{nombre}")
    @PreAuthorize("hasAuthority('PERMISO_ROLES_GESTIONAR')")
    public RolResponse actualizar(@PathVariable String nombre, @Valid @RequestBody RolUpdate dto) {
        return servicio.actualizar(nombre, dto);
    }

    @DeleteMapping("/{nombre}")
    @PreAuthorize("hasAuthority('PERMISO_ROLES_GESTIONAR')")
    public ResponseEntity<Void> eliminar(@PathVariable String nombre) {
        servicio.eliminar(nombre);
        return ResponseEntity.noContent().build();
    }
}
