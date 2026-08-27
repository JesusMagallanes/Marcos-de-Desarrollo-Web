package com.backend.catalogo.valoracion;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.backend.catalogo.valoracion.dto.ValoracionDtos.ValoracionRequest;
import com.backend.catalogo.valoracion.dto.ValoracionDtos.ValoracionResponse;
import com.backend.catalogo.shared.seguridad.JwtUtils;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

/**
 * Valoraciones de clientes sobre productos. La identidad (usuario_id) siempre
 * sale del JWT: ni la URL ni el cuerpo pueden suplantar a otro usuario.
 *
 * GET es público (`/api/productos/**`); POST y DELETE quedan bajo
 * `.anyRequest().authenticated()` de SecurityConfig.
 */
@RestController
@RequestMapping("/api/productos/{productoId}/valoraciones")
@Validated
@RequiredArgsConstructor
public class ValoracionController {

    private final ValoracionService servicio;

    @GetMapping
    public List<ValoracionResponse> listar(@PathVariable @Positive Long productoId) {
        return servicio.listar(productoId);
    }

    /** La valoración del usuario en curso, o 200 con cuerpo vacío si no tiene. */
    @GetMapping("/mia")
    public ValoracionResponse mia(@PathVariable @Positive Long productoId,
            @AuthenticationPrincipal Jwt jwt) {
        return servicio.obtenerMia(productoId, JwtUtils.uidDe(jwt));
    }

    /** Crea la valoración, o actualiza la existente del mismo cliente. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ValoracionResponse crear(@PathVariable @Positive Long productoId,
            @Valid @RequestBody ValoracionRequest dto,
            @AuthenticationPrincipal Jwt jwt) {
        // El token del propio usuario viaja a `compras`: la pregunta es
        // "compre yo esto", y la responde mirando SUS pedidos.
        return servicio.guardar(productoId, JwtUtils.uidDe(jwt), dto,
                jwt == null ? null : jwt.getTokenValue());
    }

    @DeleteMapping("/mia")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable @Positive Long productoId,
            @AuthenticationPrincipal Jwt jwt) {
        servicio.eliminar(productoId, JwtUtils.uidDe(jwt));
    }
}
