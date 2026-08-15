package com.backend.usuarios.auth.token;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.usuarios.shared.seguridad.ContextoRls;
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
     *
     * <p>Corre de madrugada, sin usuario detrás. Con RLS activo y sin contexto la
     * política de {@code token_revocado} no dejaría ver ninguna fila: el borrado
     * afectaría a cero registros y el log diría, tan tranquilo, que no había
     * nada que purgar. La tabla crecería para siempre sin que nadie se entere.
     *
     * <p>La marca va aquí y el trabajo en un método aparte. La transacción la
     * abre la propia operación del repositorio, ya con el contexto puesto; si
     * este método fuera {@code @Transactional}, Spring habría pedido la
     * conexión antes de entrar en el cuerpo y la marca llegaría tarde.
     */
    @Scheduled(cron = "0 0 4 * * *")
    public void purgarExpirados() {
        ContextoRls.comoSistema(this::borrarCaducados);
    }

    private void borrarCaducados() {
        int borrados = repositorio.borrarExpirados(LocalDateTime.now());
        if (borrados > 0) {
            log.info("Purgados {} tokens revocados ya caducados", borrados);
        }
    }
}
