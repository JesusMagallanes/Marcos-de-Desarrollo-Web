package com.backend.compras.carrito;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backend.compras.carrito.dto.CarritoDtos.AgregarItemRequest;
import com.backend.compras.carrito.dto.CarritoDtos.CambiarCantidadRequest;
import com.backend.compras.carrito.dto.CarritoDtos.CarritoResponse;
import com.backend.compras.shared.security.UsuarioAutenticado;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * El carrito siempre es el del usuario del token. No hay ningún endpoint que
 * acepte un usuarioId por parámetro.
 */
@RestController
@RequestMapping("/api/carrito")
@RequiredArgsConstructor
public class CarritoController {

    private final CarritoService servicio;

    @GetMapping
    public CarritoResponse ver(UsuarioAutenticado usuario) {
        return servicio.ver(usuario.id());
    }

    @PostMapping("/items")
    public CarritoResponse agregar(UsuarioAutenticado usuario,
            @Valid @RequestBody AgregarItemRequest peticion) {
        return servicio.agregar(usuario.id(), peticion);
    }

    @PutMapping("/items/{itemId}")
    public CarritoResponse cambiarCantidad(UsuarioAutenticado usuario,
            @PathVariable Long itemId,
            @Valid @RequestBody CambiarCantidadRequest peticion) {
        return servicio.cambiarCantidad(usuario.id(), itemId, peticion.cantidad());
    }

    @DeleteMapping("/items/{itemId}")
    public CarritoResponse eliminar(UsuarioAutenticado usuario, @PathVariable Long itemId) {
        return servicio.eliminar(usuario.id(), itemId);
    }

    @DeleteMapping
    public CarritoResponse vaciar(UsuarioAutenticado usuario) {
        return servicio.vaciar(usuario.id());
    }
}
