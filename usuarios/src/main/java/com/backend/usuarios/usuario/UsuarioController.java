package com.backend.usuarios.usuario;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backend.usuarios.shared.security.UsuarioAutenticado;
import com.backend.usuarios.usuario.dto.UsuarioDtos.CambioRol;
import com.backend.usuarios.usuario.dto.UsuarioDtos.PerfilUpdate;
import com.backend.usuarios.usuario.dto.UsuarioDtos.UsuarioCreate;
import com.backend.usuarios.usuario.dto.UsuarioDtos.UsuarioResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService servicio;

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISO_USUARIOS_GESTIONAR')")
    public List<UsuarioResponse> listar() {
        return servicio.listar();
    }

    /** El propio usuario o quien gestione usuarios. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISO_USUARIOS_GESTIONAR') or #id == principal.claims['uid']")
    public UsuarioResponse obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERMISO_USUARIOS_GESTIONAR')")
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody UsuarioCreate dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(dto));
    }

    /**
     * El id de la ruta se contrasta contra el del token: nadie edita el perfil
     * de otro por cambiar el número en la URL.
     */
    @PutMapping("/{id}/perfil")
    @PreAuthorize("hasAuthority('PERMISO_USUARIOS_GESTIONAR') or #id == principal.claims['uid']")
    public UsuarioResponse actualizarPerfil(@PathVariable Long id, @Valid @RequestBody PerfilUpdate dto) {
        return servicio.actualizarPerfil(id, dto);
    }

    @PatchMapping("/{id}/rol")
    @PreAuthorize("hasAuthority('PERMISO_USUARIOS_GESTIONAR')")
    public UsuarioResponse cambiarRol(@PathVariable Long id,
            @Valid @RequestBody CambioRol dto,
            UsuarioAutenticado autenticado) {

        // Un admin no puede quitarse a sí mismo el rol y dejar el sistema sin nadie.
        if (id.equals(autenticado.id()) && !"ADMINISTRADOR".equals(dto.rol())) {
            throw new com.backend.usuarios.shared.error.ConflictoException(
                    "No puedes cambiar tu propio rol de administrador");
        }
        return servicio.cambiarRol(id, dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISO_USUARIOS_GESTIONAR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, UsuarioAutenticado autenticado) {
        if (id.equals(autenticado.id())) {
            throw new com.backend.usuarios.shared.error.ConflictoException("No puedes eliminarte a ti mismo");
        }
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
