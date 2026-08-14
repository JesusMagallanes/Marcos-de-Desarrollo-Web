package com.backend.catalogo.valoracion;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backend.catalogo.valoracion.dto.ValoracionDtos.ValoracionDestacadaResponse;

import lombok.RequiredArgsConstructor;

/**
 * Las mejores reseñas de la portada. Es una vitrina pública (GET), por eso la
 * regla de SecurityConfig la lista como `permitAll`; no cuelga de
 * `/api/productos/**` porque no es de un producto concreto.
 */
@RestController
@RequestMapping("/api/valoraciones")
@RequiredArgsConstructor
public class ValoracionDestacadaController {

    private final ValoracionService servicio;

    /** Las 6 aprobadas con más estrellas, cada una con su producto. */
    @GetMapping("/top")
    public List<ValoracionDestacadaResponse> top() {
        return servicio.destacadas();
    }
}
