package com.backend.catalogo.producto;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.backend.catalogo.producto.dto.ProductoDtos.ProductoRequest;
import com.backend.catalogo.producto.dto.ProductoDtos.ProductoResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

/**
 * Lo que un colaborador hace con SUS productos.
 *
 * <p>Va aparte de {@code ProductoController} porque son dos cosas distintas: allí
 * la tienda gestiona su catálogo y lo que guarda se publica; aquí un tercero
 * propone, y lo que guarda pasa por revisión. Mezclarlas en un controlador
 * obligaría a preguntar «¿quién eres?» en cada método.
 *
 * <p>El propietario sale SIEMPRE del token. No hay ningún endpoint que acepte un
 * id de propietario por la URL ni por el cuerpo: si lo hubiera, cualquiera
 * publicaría a nombre de otro.
 */
@RestController
@RequestMapping("/api/productos/mios")
@Validated
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERMISO_PRODUCTOS_PROPIOS')")
public class ProductoColaboradorController {

    private final ProductoService servicio;

    /** Todo lo suyo, aprobado o no: necesita ver los rechazados para corregir. */
    @GetMapping
    public List<ProductoResponse> mios(@AuthenticationPrincipal Jwt jwt) {
        return servicio.mios(uidDe(jwt));
    }

    /** Se crea PENDIENTE. La respuesta ya lo dice, para no prometer visibilidad. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductoResponse crear(@Valid @RequestBody ProductoRequest dto,
            @AuthenticationPrincipal Jwt jwt) {
        return servicio.crearComoColaborador(uidDe(jwt), dto);
    }

    /** Editar devuelve el producto a la cola: lo aprobado no se puede cambiar a espaldas. */
    @PutMapping("/{id}")
    public ProductoResponse actualizar(@PathVariable @Positive Long id,
            @Valid @RequestBody ProductoRequest dto,
            @AuthenticationPrincipal Jwt jwt) {
        return servicio.actualizarComoColaborador(id, uidDe(jwt), dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable @Positive Long id, @AuthenticationPrincipal Jwt jwt) {
        servicio.eliminarComoColaborador(id, uidDe(jwt));
    }

    private Long uidDe(Jwt jwt) {
        Object uid = jwt == null ? null : jwt.getClaim("uid");
        return uid == null ? null : ((Number) uid).longValue();
    }
}
