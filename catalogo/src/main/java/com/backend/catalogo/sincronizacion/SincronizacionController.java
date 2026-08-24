package com.backend.catalogo.sincronizacion;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backend.catalogo.sincronizacion.dto.SincronizacionDtos.PeticionSyncValoracion;
import com.backend.catalogo.sincronizacion.dto.SincronizacionDtos.RespuestaSyncValoracion;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Confirmación de operaciones escritas offline por el cliente.
 *
 * <p>El cliente no puede usar los endpoints normales para reenviar su cola:
 * POST de valoración devuelve 201 y DELETE 404/409 según el caso, y ninguno
 * reconoce un reenvío. Este endpoint responde SIEMPRE 200 con el resultado
 * estructurado ({@code duplicado}), porque "esto ya lo apliqué" es una
 * respuesta buena, no un error: el cliente la necesita para vaciar su cola.
 *
 * <p>Queda bajo {@code .anyRequest().authenticated()}: la identidad sale del
 * JWT, igual que en el resto de escrituras, y cada operación queda asociada a
 * SU usuario — el id de operación de otro no sirve para leer ni tapar nada.
 */
@RestController
@RequestMapping("/api/sync")
@Validated
@RequiredArgsConstructor
public class SincronizacionController {

    private final SincronizacionService servicio;

    /** Aplica (o reconoce como ya aplicada) una valoración escrita offline. */
    @PostMapping("/valoraciones")
    public RespuestaSyncValoracion valoraciones(
            @Valid @RequestBody PeticionSyncValoracion peticion,
            @AuthenticationPrincipal Jwt jwt) {
        return servicio.aplicarValoracion(uidDe(jwt), peticion,
                jwt == null ? null : jwt.getTokenValue());
    }

    private Long uidDe(Jwt jwt) {
        Object uid = jwt == null ? null : jwt.getClaim("uid");
        return uid == null ? null : ((Number) uid).longValue();
    }
}
