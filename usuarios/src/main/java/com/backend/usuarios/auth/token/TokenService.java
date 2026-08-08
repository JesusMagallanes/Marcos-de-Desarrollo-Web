package com.backend.usuarios.auth.token;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.usuarios.auth.JwtService;
import com.backend.usuarios.shared.metricas.MetricasSeguridad;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenService {

    private final TokenRevocadoRepository repositorio;
    private final JwtService jwtService;
    private final MetricasSeguridad metricas;

    @Transactional(readOnly = true)
    public boolean estaRevocado(String jti) {
        return jti != null && repositorio.existsByJti(jti);
    }

    @Transactional
    public void revocar(Claims claims, TokenRevocado.Motivo motivo) {
        String jti = claims.getId();
        if (jti == null || repositorio.existsByJti(jti)) {
            return;
        }

        Long usuarioId = claims.get("uid", Number.class) == null
                ? null
                : claims.get("uid", Number.class).longValue();

        repositorio.save(TokenRevocado.builder()
                .jti(jti)
                .usuarioId(usuarioId)
                .motivo(motivo)
                .expiraEn(jwtService.expiracionDe(claims))
                .build());

        metricas.tokenRevocado(motivo.name());
        log.debug("Token {} revocado por {}", jti, motivo);
    }

    /**
     * Purga los que ya caducaron: una vez pasada su expiración el propio JWT
     * es inválido y guardarlos solo hace crecer la tabla.
     */
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void purgarExpirados() {
        int borrados = repositorio.borrarExpirados(LocalDateTime.now());
        if (borrados > 0) {
            log.info("Purgados {} tokens revocados ya caducados", borrados);
        }
    }
}
