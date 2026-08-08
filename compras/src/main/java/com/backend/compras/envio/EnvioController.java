package com.backend.compras.envio;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.backend.compras.envio.dto.EnvioDtos.CambioEstadoEnvio;
import com.backend.compras.envio.dto.EnvioDtos.EnvioResponse;
import com.backend.compras.shared.security.UsuarioAutenticado;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/envios")
@RequiredArgsConstructor
public class EnvioController {

    private final EnvioService servicio;

    @GetMapping
    @PreAuthorize("hasAnyRole('EMPLEADO', 'ADMINISTRADOR')")
    public List<EnvioResponse> listar(@RequestParam(required = false) EstadoEnvio estado) {
        return servicio.listar(estado);
    }

    @GetMapping("/mios")
    public List<EnvioResponse> mios(UsuarioAutenticado usuario) {
        return servicio.misEnvios(usuario.id());
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('EMPLEADO', 'ADMINISTRADOR')")
    public EnvioResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstadoEnvio dto) {
        return servicio.cambiarEstado(id, dto.estadoEnvio());
    }
}
